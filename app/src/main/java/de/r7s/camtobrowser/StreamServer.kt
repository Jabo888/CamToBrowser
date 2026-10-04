package de.r7s.camtobrowser

import android.util.Base64
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object Clock {
    private val base = System.nanoTime()
    fun us(): Long = (System.nanoTime() - base) / 1000
}

/**
 * Ein Port (8080):
 *  /ws            WebSocket: H.264 + PCM-Ton (Quelle fuer den Player)
 *  /player.html   Player als Download (fuer eine Browser-Quelle, "Lokale Datei")
 *  /player        Player inline (zum Testen im PC-Browser)
 */
class StreamServer(private val port: Int = 8080) {
    var onClientsChanged: ((Int) -> Unit)? = null
    var onNeedKeyFrame: (() -> Unit)? = null

    private class Client(val socket: Socket) {
        val q = ArrayBlockingQueue<ByteArray>(90)
        @Volatile var started = false
        @Volatile var alive = true
    }

    private val ws = CopyOnWriteArrayList<Client>()
    private var server: ServerSocket? = null
    @Volatile private var running = false

    @Volatile var videoW = 1920
    @Volatile var videoH = 1080
    @Volatile var videoFps = 30
    @Volatile var playerRot = 0
    @Volatile var playerMirror = false

    /** Zugriffs-PIN (nur Ziffern). null = kein Schutz. */
    @Volatile var pin: String? = null

    private class Fails(var count: Int, var since: Long)
    private val fails = ConcurrentHashMap<String, Fails>()

    private fun blocked(ip: String): Boolean {
        val f = fails[ip] ?: return false
        if (System.currentTimeMillis() - f.since > 60_000) { fails.remove(ip); return false }
        return f.count >= 10
    }

    private fun registerFail(ip: String) {
        val now = System.currentTimeMillis()
        val f = fails.getOrPut(ip) { Fails(0, now) }
        if (now - f.since > 60_000) { f.count = 0; f.since = now }
        f.count++
    }

    private fun pinOk(given: String?): Boolean {
        val p = pin ?: return true
        val g = given ?: return false
        return MessageDigest.isEqual(p.toByteArray(), g.toByteArray())
    }

    /** Alle verbundenen Player trennen (z. B. nach PIN-Änderung). */
    fun kickClients() { for (c in ws) { c.alive = false; try { c.socket.close() } catch (_: Exception) {} } }

    private fun gatePage(path: String, de: Boolean, wrong: Boolean): String {
        val title = if (de) "PIN eingeben" else "Enter PIN"
        val sub = if (de) "Die PIN steht in der App auf dem Handy (Reiter Verbinden)." else "The PIN is shown in the app on the phone (Connect tab)."
        val err = if (!wrong) "" else if (de) "PIN falsch." else "Wrong PIN."
        val btn = if (path == "/player.html") (if (de) "Player-Datei laden" else "Download player file") else "OK"
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>CamToBrowser</title>" +
            "<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0A0D14;color:#fff;font-family:sans-serif}" +
            "form{text-align:center;padding:24px}input{width:200px;padding:12px;font-size:30px;text-align:center;letter-spacing:6px;border-radius:10px;border:2px solid #4c8dff;background:#141a26;color:#fff}" +
            "button{display:block;margin:16px auto 0;padding:12px 24px;font-size:18px;border:0;border-radius:10px;background:#4c8dff;color:#fff;cursor:pointer}p{color:#9aa4b5}b{color:#f5b942}</style></head><body>" +
            "<form method='get' action='" + path + "'><h2>" + title + "</h2><input name='pin' type='text' inputmode='numeric' maxlength='6' autocomplete='off' autofocus placeholder='000000'>" +
            "<button type='submit'>" + btn + "</button><p>" + sub + "</p><b>" + err + "</b></form></body></html>"
    }

    private fun deny(out: OutputStream, code: String, text: String) {
        val body = text.toByteArray()
        out.write("HTTP/1.1 $code\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body); out.flush()
    }

    /** Dreht/spiegelt das Bild im Player live (kein Neuladen der Datei nötig). */
    fun setPlayerView(rot: Int, mirror: Boolean) {
        playerRot = rot; playerMirror = mirror
        val f = wsFrame(4, 0, viewString().toByteArray())
        for (c in ws) c.q.offer(f)
    }
    private fun viewString() = "$playerRot|${if (playerMirror) 1 else 0}"

    fun clientCount() = ws.size
    private fun notifyClients() { onClientsChanged?.invoke(ws.size) }

    // ---------- Eingaenge von den Encodern ----------
    fun pushVideo(data: ByteArray, key: Boolean) {
        if (ws.isEmpty()) return
        if (key) {
            val cfg = avcCodec(data)
            val cfgMsg = if (cfg != null) wsFrame(3, 0, "$cfg|$videoW|$videoH|$videoFps|${viewString()}".toByteArray()) else null
            val kf = wsFrame(1, 1, data)
            for (c in ws) {
                if (!c.started && cfgMsg != null) enqueue(c, cfgMsg)
                c.started = true
                enqueue(c, kf)
            }
        } else {
            val f = wsFrame(1, 0, data)
            for (c in ws) if (c.started) enqueue(c, f)
        }
    }

    fun pushPcm(pcm: ByteArray) {
        if (ws.isEmpty()) return
        val f = wsFrame(2, 0, pcm)
        for (c in ws) if (c.started) c.q.offer(f) // Ton darf bei Stau verworfen werden
    }

    private fun enqueue(c: Client, b: ByteArray) {
        if (!c.q.offer(b)) { c.q.clear(); c.started = false; onNeedKeyFrame?.invoke() }
    }

    /** Neuer Encoder: Clients warten auf den naechsten Keyframe (Player verbindet sich nicht neu). */
    fun resetClients() { for (c in ws) { c.q.clear(); c.started = false } }

    private fun wsFrame(type: Int, flag: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        val hdr = when {
            len < 126 -> byteArrayOf(0x82.toByte(), len.toByte())
            len < 65536 -> byteArrayOf(0x82.toByte(), 126, (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x82.toByte(), 127, 0, 0, 0, 0, (len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
        val out = ByteArray(hdr.size + len)
        System.arraycopy(hdr, 0, out, 0, hdr.size)
        out[hdr.size] = type.toByte(); out[hdr.size + 1] = flag.toByte()
        System.arraycopy(payload, 0, out, hdr.size + 2, payload.size)
        return out
    }

    private fun avcCodec(d: ByteArray): String? {
        var i = 0
        while (i + 7 < d.size) {
            if (d[i] == 0.toByte() && d[i + 1] == 0.toByte() && d[i + 2] == 1.toByte() && (d[i + 3].toInt() and 0x1F) == 7) {
                return "avc1." + "%02X%02X%02X".format(d[i + 4].toInt() and 0xFF, d[i + 5].toInt() and 0xFF, d[i + 6].toInt() and 0xFF)
            }
            i++
        }
        return null
    }

    // ---------- Server ----------
    fun start() {
        if (running) return
        running = true
        thread(isDaemon = true) {
            try {
                server = ServerSocket(port)
                while (running) {
                    val s = server!!.accept()
                    thread(isDaemon = true) { handle(s) }
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        running = false
        for (c in ws) { c.alive = false; try { c.socket.close() } catch (_: Exception) {} }
        try { server?.close() } catch (_: Exception) {}
    }

    private fun respond(out: OutputStream, type: String, body: ByteArray, extra: String = "") {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-cache\r\n$extra\r\n".toByteArray())
        out.write(body); out.flush()
    }

    private fun handle(s: Socket) {
        try {
            s.tcpNoDelay = true
            val reader = s.getInputStream().bufferedReader()
            val req = reader.readLine() ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val l = reader.readLine() ?: break
                if (l.isEmpty()) break
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val full = req.split(" ").getOrNull(1) ?: "/"
            val path = full.substringBefore('?')
            val params = full.substringAfter('?', "").split('&').filter { it.contains('=') }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val out = s.getOutputStream()
            val ip = s.inetAddress?.hostAddress ?: "?"
            if (path == "/ws") {
                if (blocked(ip)) { deny(out, "429 Too Many Requests", "Too many attempts. Try again in a minute."); return }
                if (!pinOk(params["pin"])) { registerFail(ip); deny(out, "403 Forbidden", "PIN required"); return }
            }
            when (path) {
                "/ws" -> {
                    val key = headers["sec-websocket-key"] ?: return
                    val accept = Base64.encodeToString(
                        MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()), Base64.NO_WRAP)
                    out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n").toByteArray())
                    out.flush()
                    val c = Client(s)
                    c.q.offer(wsFrame(4, 0, viewString().toByteArray()))
                    ws.add(c); notifyClients()
                    onNeedKeyFrame?.invoke()
                    try {
                        while (c.alive && running) {
                            val b = c.q.poll(1, TimeUnit.SECONDS) ?: continue
                            out.write(b); out.flush()
                        }
                    } finally { ws.remove(c); notifyClients() }
                }
                "/player.html", "/player" -> {
                    val given = params["pin"]
                    if (pin != null && blocked(ip)) { deny(out, "429 Too Many Requests", "Too many attempts. Try again in a minute."); return }
                    if (pin != null && !pinOk(given)) {
                        val de = (headers["accept-language"] ?: "de").lowercase().startsWith("de")
                        if (given != null) registerFail(ip)
                        respond(out, "text/html; charset=utf-8", gatePage(path, de, given != null).toByteArray(), "")
                        return
                    }
                    val host = (headers["host"] ?: "localhost").substringBefore(':')
                    val html = PLAYER.replace("%HOST%", host)
                        .replace("%PIN%", if (pin != null) (given ?: "").filter { it.isDigit() } else "")
                        .replace("%ROT%", (params["rot"]?.toIntOrNull() ?: 0).toString())
                        .replace("%MIRROR%", if (params["mirror"] == "1") "1" else "0")
                    respond(out, "text/html; charset=utf-8", html.toByteArray(),
                        if (path == "/player.html") "Content-Disposition: attachment; filename=\"handycam-player.html\"\r\n" else "")
                }
                else -> respond(out, "text/html; charset=utf-8",
                    ("<html><body style='font-family:sans-serif'><h3>Handy Cam Stream</h3>" +
                        "<a href='/player.html'>Player-Datei fuer eine Browser-Quelle laden</a></body></html>").toByteArray())
            }
        } catch (_: Exception) {
        } finally { try { s.close() } catch (_: Exception) {} }
    }

    companion object {
        private val PLAYER = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Handy Cam</title>
<style>
html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden}
#stage{position:absolute;left:50%;top:50%}
canvas,video{width:100%;height:100%;object-fit:contain;display:none;background:transparent}
#msg{position:fixed;left:8px;top:8px;color:#fff;font:14px sans-serif;text-shadow:0 0 3px #000}
#pinbox{display:none;position:fixed;left:0;top:0;width:100%;height:100%;align-items:center;justify-content:center;background:rgba(10,13,20,.88);font-family:sans-serif}
#pinin{text-align:center;color:#fff}
#pint{font-size:20px;margin-bottom:12px}
#pinf{width:190px;padding:10px;font-size:28px;text-align:center;letter-spacing:6px;border-radius:10px;border:2px solid #4c8dff;background:#141a26;color:#fff}
#pinb{margin-left:10px;padding:12px 20px;font-size:20px;border:0;border-radius:10px;background:#4c8dff;color:#fff;cursor:pointer}
#pinm{margin-top:12px;font-size:14px;color:#f5b942}
</style></head>
<body><div id="stage"><canvas id="c"></canvas><video id="v" muted autoplay playsinline></video></div><div id="msg">Verbinde ...</div>
<div id="pinbox"><div id="pinin"><div id="pint">PIN</div><input id="pinf" type="text" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="000000"><button id="pinb">OK</button><div id="pinm"></div></div></div>
<script>
var HOST="%HOST%", PIN="%PIN%", ROT=%ROT%, MIRROR=%MIRROR%;
function nalUnits(d){
  var out=[],i=0,n=d.length,start=-1;
  while(i+2<n){
    if(d[i]===0&&d[i+1]===0&&d[i+2]===1){
      if(start>=0){var e=i;while(e>start&&d[e-1]===0)e--;out.push(d.subarray(start,e))}
      start=i+3;i+=3;
    }else i++;
  }
  if(start>=0&&start<n){var e2=n;while(e2>start&&d[e2-1]===0)e2--;out.push(d.subarray(start,e2))}
  return out;
}
function u8(){var l=0,i,a=arguments;for(i=0;i<a.length;i++)l+=a[i].length;var o=new Uint8Array(l),p=0;for(i=0;i<a.length;i++){o.set(a[i],p);p+=a[i].length}return o}
function u32(v){return new Uint8Array([(v>>>24)&255,(v>>>16)&255,(v>>>8)&255,v&255])}
function u16(v){return new Uint8Array([(v>>8)&255,v&255])}
function zeros(n){return new Uint8Array(n)}
function str(s){return new Uint8Array([s.charCodeAt(0),s.charCodeAt(1),s.charCodeAt(2),s.charCodeAt(3)])}
function box(type){
  var parts=[],l=8,i;
  for(i=1;i<arguments.length;i++){parts.push(arguments[i]);l+=arguments[i].length}
  return u8.apply(null,[u32(l),str(type)].concat(parts));
}
function initSegment(sps,pps,w,h){
  var matrix=u8(u32(0x10000),u32(0),u32(0),u32(0),u32(0x10000),u32(0),u32(0),u32(0),u32(0x40000000));
  var ftyp=box('ftyp',str('isom'),u32(512),str('isom'),str('iso6'),str('avc1'),str('mp41'));
  var mvhd=box('mvhd',u32(0),u32(0),u32(0),u32(90000),u32(0),u32(0x10000),u16(0x0100),zeros(2),zeros(8),matrix,zeros(24),u32(2));
  var tkhd=box('tkhd',u32(3),u32(0),u32(0),u32(1),u32(0),u32(0),zeros(8),u16(0),u16(0),u16(0),u16(0),matrix,u32(w*65536),u32(h*65536));
  var mdhd=box('mdhd',u32(0),u32(0),u32(0),u32(90000),u32(0),u16(0x55c4),u16(0));
  var hdlr=box('hdlr',u32(0),u32(0),str('vide'),zeros(12),new Uint8Array([86,105,100,101,111,0]));
  var vmhd=box('vmhd',u32(1),zeros(8));
  var dinf=box('dinf',box('dref',u32(0),u32(1),box('url ',u32(1))));
  var avcC=box('avcC',new Uint8Array([1,sps[1],sps[2],sps[3],0xFF,0xE1]),u16(sps.length),sps,new Uint8Array([1]),u16(pps.length),pps);
  var avc1=box('avc1',zeros(6),u16(1),zeros(16),u16(w),u16(h),u32(0x480000),u32(0x480000),u32(0),u16(1),zeros(32),u16(0x18),u16(0xFFFF),avcC);
  var stbl=box('stbl',box('stsd',u32(0),u32(1),avc1),box('stts',u32(0),u32(0)),box('stsc',u32(0),u32(0)),box('stsz',u32(0),u32(0),u32(0)),box('stco',u32(0),u32(0)));
  var minf=box('minf',vmhd,dinf,stbl);
  var mdia=box('mdia',mdhd,hdlr,minf);
  var trak=box('trak',tkhd,mdia);
  var mvex=box('mvex',box('trex',u32(0),u32(1),u32(1),u32(0),u32(0),u32(0)));
  return u8(ftyp,box('moov',mvhd,trak,mvex));
}
function mediaSegment(seq,tdec,dur,key,sample){
  var mfhd=box('mfhd',u32(0),u32(seq));
  var tfhd=box('tfhd',u32(0x020000),u32(1));
  var tfdt=box('tfdt',u32(0x01000000),u32(Math.floor(tdec/4294967296)),u32(tdec>>>0));
  function trun(off){return box('trun',u32(0x000701),u32(1),u32(off),u32(dur),u32(sample.length),u32(key?0x02000000:0x01010000))}
  var traf=box('traf',tfhd,tfdt,trun(0));
  var moofSize=8+mfhd.length+traf.length;
  traf=box('traf',tfhd,tfdt,trun(moofSize+8));
  return u8(box('moof',mfhd,traf),box('mdat',sample));
}
/* Annex-B-Frame -> {sps,pps,sample(AVCC),key} */
function toAvcc(d){
  var nals=nalUnits(d),sps=null,pps=null,parts=[],key=false,i;
  for(i=0;i<nals.length;i++){
    var t=nals[i][0]&31;
    if(t===7)sps=nals[i];else if(t===8)pps=nals[i];
    else if(t===1||t===5){if(t===5)key=true;parts.push(u32(nals[i].length),nals[i])}
  }
  return {sps:sps,pps:pps,sample:u8.apply(null,parts),key:key};
}
if(typeof module!=='undefined')module.exports={nalUnits:nalUnits,initSegment:initSegment,mediaSegment:mediaSegment,toAvcc:toAvcc,u8:u8};
var c=document.getElementById('c'), vid=document.getElementById('v'), stage=document.getElementById('stage'), msg=document.getElementById('msg');
var ctx=c.getContext('2d');
var WC=(typeof VideoDecoder!=='undefined');
var dec=null, ts=0, needKey=true, actx=null, nextT=0, ws=null;
var info={codec:'',w:1920,h:1080,fps:30};
function show(t){msg.style.display='block';msg.textContent=t}
function layout(){
  var swap=(ROT==90||ROT==270);
  stage.style.width=(swap?'100vh':'100vw');stage.style.height=(swap?'100vw':'100vh');
  stage.style.transform='translate(-50%,-50%) rotate('+ROT+'deg)'+(MIRROR?' scaleX(-1)':'');
  (WC?c:vid).style.display='block';
}
layout();

/* ---------- Pfad 1: WebCodecs (schnellster Weg) ---------- */
function setupWC(){
  if(dec){try{dec.close()}catch(e){}}
  dec=new VideoDecoder({
    output:drawWC,
    error:function(e){show('Decoder: '+e.message);needKey=true;setTimeout(setupWC,500)}
  });
  dec.configure({codec:info.codec,optimizeForLatency:true});
  needKey=true;
}
function drawWC(f){
  var w=f.displayWidth,h=f.displayHeight;
  if(c.width!=w||c.height!=h){c.width=w;c.height=h}
  ctx.clearRect(0,0,w,h);ctx.drawImage(f,0,0,w,h);f.close();
  msg.style.display='none';
}
function frameWC(d,flag){
  if(!dec||dec.state!=='configured')return;
  if(needKey&&flag!==1)return;
  if(dec.decodeQueueSize>3&&flag!==1){needKey=true;return}
  needKey=false;
  try{dec.decode(new EncodedVideoChunk({type:flag===1?'key':'delta',timestamp:ts,data:d}));ts+=33333}
  catch(err){needKey=true}
}

/* ---------- Pfad 2: MediaSource + fMP4 (laeuft auch ohne sicheren Kontext, z. B. in Streaming-Software) ---------- */
var ms=null, sb=null, q=[], seq=1, tdec=0, dur=3000, mseInit=false, sbReady=false, appended=0, fatal=false;
function mseReset(){
  mseInit=false;sbReady=false;q=[];seq=1;tdec=0;appended=0;needKey=true;
  try{vid.removeAttribute('src');vid.load()}catch(e){}
  ms=null;sb=null;
}
function mseStart(sps,pps){
  var mime='video/mp4; codecs="'+info.codec+'"';
  if(!window.MediaSource||!MediaSource.isTypeSupported(mime)){fatal=true;show('Dieser Browser kann H.264 nicht abspielen ('+info.codec+')');return}
  dur=Math.round(90000/(info.fps||30));
  mseInit=true;
  var mine=new MediaSource();ms=mine;
  vid.src=URL.createObjectURL(mine);
  mine.addEventListener('sourceopen',function(){
    if(mine!==ms||sb)return;
    sb=mine.addSourceBuffer(mime);sb.mode='segments';
    sb.addEventListener('updateend',pump);
    q.unshift(initSegment(sps,pps,info.w,info.h));
    sbReady=true;pump();
  });
}
function pump(){
  if(!sbReady||!sb||sb.updating||!q.length)return;
  try{
    if(appended>0&&appended%90===0&&vid.buffered.length){
      var s0=vid.buffered.start(0),cur=vid.currentTime;
      if(cur-s0>2){sb.remove(s0,cur-1);return}
    }
    sb.appendBuffer(q.shift());appended++;
  }catch(e){
    if(e&&e.name==='QuotaExceededError'&&vid.buffered.length){try{sb.remove(vid.buffered.start(0),Math.max(vid.buffered.start(0),vid.currentTime-0.5))}catch(e2){}}
    else{show('MSE-Fehler: '+(e&&e.message));mseReset()}
  }
}
function frameMSE(d,flag){
  if(fatal)return;
  if(needKey&&flag!==1)return;
  var r=toAvcc(d);
  if(!mseInit){
    if(!(r.sps&&r.pps))return;
    mseStart(r.sps,r.pps);
    if(fatal)return;
  }
  if(!r.sample.length)return;
  needKey=false;
  q.push(mediaSegment(seq++,tdec,dur,flag===1,r.sample));tdec+=dur;
  if(q.length>45){q=[];needKey=true}   // Stau: verwerfen, auf naechsten Keyframe warten
  pump();
}
vid.addEventListener('playing',function(){msg.style.display='none'});
setInterval(function(){
  if(WC||!sbReady||!vid.buffered.length)return;
  var end=vid.buffered.end(vid.buffered.length-1),lag=end-vid.currentTime;
  if(vid.paused){var p=vid.play();if(p&&p.catch)p.catch(function(){})}
  if(lag>0.4){vid.currentTime=end-0.06;vid.playbackRate=1}
  else if(lag>0.2){vid.playbackRate=1.12}
  else{vid.playbackRate=1}
},100);

/* ---------- Ton (PCM) ---------- */
function playPcm(d){
  if(!actx){actx=new AudioContext({latencyHint:'interactive',sampleRate:48000});nextT=0}
  var n=(d.byteLength-2)>>1, i16=new Int16Array(d,2,n);
  var ab=actx.createBuffer(1,n,48000), ch=ab.getChannelData(0);
  for(var i=0;i<n;i++)ch[i]=i16[i]/32768;
  var src=actx.createBufferSource();src.buffer=ab;src.connect(actx.destination);
  var now=actx.currentTime;
  if(nextT<now+0.01||nextT>now+0.25)nextT=now+0.05;
  src.start(nextT);nextT+=ab.duration;
}

/* ---------- Verbindung ---------- */
var fails=0, gaveUp=false, DE=(navigator.language||'de').slice(0,2)==='de';
var KEY='hc_pin_'+HOST;
function lsGet(){try{return localStorage.getItem(KEY)||''}catch(e){return ''}}
function lsSet(v){try{if(v)localStorage.setItem(KEY,v);else localStorage.removeItem(KEY)}catch(e){}}
(function(){if(PIN)return;var m=/[?#&]pin=(\d+)/.exec(location.href);PIN=m?m[1]:lsGet()})();
function askPin(){
  gaveUp=true;
  var bx=document.getElementById('pinbox'),f=document.getElementById('pinf');
  document.getElementById('pint').textContent=DE?'PIN vom Handy eingeben':'Enter the PIN shown on the phone';
  document.getElementById('pinm').textContent=DE?'PIN falsch oder Handy nicht erreichbar. Rechtsklick auf die Browser-Quelle > Interagieren.':'Wrong PIN or phone not reachable. Right-click the browser source > Interact.';
  bx.style.display='flex';f.value='';try{f.focus()}catch(e){}
}
function submitPin(){
  var f=document.getElementById('pinf'),v=(f.value||'').replace(/\D/g,'');
  if(!v)return;
  PIN=v;fails=0;gaveUp=false;
  document.getElementById('pinbox').style.display='none';
  show(DE?'Verbinde ...':'Connecting ...');connect();
}
document.getElementById('pinb').onclick=submitPin;
document.getElementById('pinf').onkeydown=function(e){if(e.key==='Enter')submitPin()};
function connect(){
  if(gaveUp)return;
  var opened=false;
  ws=new WebSocket('ws://'+HOST+':8080/ws'+(PIN?'?pin='+PIN:''));ws.binaryType='arraybuffer';
  ws.onopen=function(){opened=true;fails=0;lsSet(PIN);show('Warte auf Bild ...')};
  ws.onclose=function(){
    if(!opened){fails++}
    if(fails>=2){askPin();return}
    show('Getrennt - verbinde neu ...');setTimeout(connect,1000);
  };
  ws.onerror=function(){try{ws.close()}catch(e){}};
  ws.onmessage=function(e){
    var d=e.data,v=new DataView(d),type=v.getUint8(0),flag=v.getUint8(1);
    if(type===3){
      var p=new TextDecoder().decode(new Uint8Array(d,2)).split('|');
      info={codec:p[0],w:+p[1]||1920,h:+p[2]||1080,fps:+p[3]||30};
      if(p.length>5){ROT=+p[4]||0;MIRROR=+p[5]?1:0;layout()}
      fatal=false;
      if(WC)setupWC();else mseReset();
    }else if(type===1){
      var data=new Uint8Array(d,2);
      if(WC)frameWC(data,flag);else frameMSE(data,flag);
    }else if(type===4){
      var vp=new TextDecoder().decode(new Uint8Array(d,2)).split('|');
      ROT=+vp[0]||0;MIRROR=+vp[1]?1:0;layout();
    }else if(type===2){playPcm(d)}
  };
}
connect();
</script></body></html>"""
    }
}
