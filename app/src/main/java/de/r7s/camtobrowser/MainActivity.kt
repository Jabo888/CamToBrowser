package de.r7s.camtobrowser

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.provider.Settings
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Size
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.NetworkInterface

// ---- Farben ----
private val Ink = Color(0xFF0A0D14)
private val Glass = Color(0xEB11161F)
private val Chip = Color(0x1AFFFFFF)
private val Line = Color(0x1FFFFFFF)
private val Accent = Color(0xFF5AA9FF)
private val OnAccent = Color(0xFF04101F)
private val TextHi = Color(0xFFEAF0FA)
private val TextLo = Color(0xFF8E9AB0)
private val Good = Color(0xFF4ADE80)
private val Bad = Color(0xFFFF6B6B)

private val TAB_KEYS = listOf("tab_connect", "tab_video", "tab_image", "tab_sound", "tab_more")

class MainActivity : ComponentActivity() {
    private var svc by mutableStateOf<StreamService?>(null)
    private var granted by mutableStateOf(false)
    private var caps by mutableIntStateOf(0)
    private var clients by mutableIntStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var saver by mutableStateOf(false)
    private var panelOpen by mutableStateOf(false)
    private var tab by mutableIntStateOf(0)
    private var torchOn by mutableStateOf(false)
    private var playerRot by mutableIntStateOf(0)
    private var playerMirror by mutableStateOf(false)
    private var bound = false
    private var notifOn by mutableStateOf(true)
    private var pinValue by mutableStateOf<String?>(null)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasCam()
        if (granted) startStreaming()
        notifOn = notifEnabled()
        if (notifOn) svc?.refreshNotification()
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as StreamService.LocalBinder).get()
            s.cam.onReady = { caps++ }
            s.cam.onError = { msg -> error = msg }
            s.server.onClientsChanged = { n -> clients = n }
            s.onStopped = { finish() }
            clients = s.server.clientCount()
            pinValue = PinStore.active(applicationContext)
            playerRot = s.server.playerRot
            playerMirror = s.server.playerMirror
            svc = s
            caps++
        }
        override fun onServiceDisconnected(name: ComponentName?) { svc = null }
    }

    private fun hasCam() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun perms(): Array<String> {
        val l = arrayListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }

    /** Beim Start fehlende Berechtigungen (Kamera, ab Android 13 auch Benachrichtigungen). */
    private fun startupPerms(): Array<String> {
        val l = arrayListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }

    private fun notifEnabled(): Boolean =
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    override fun onResume() {
        super.onResume()
        notifOn = notifEnabled()
        if (notifOn) svc?.refreshNotification()
    }

    private fun startStreaming() {
        if (bound) return
        val i = Intent(this, StreamService::class.java)
        startForegroundService(i)
        bound = bindService(i, conn, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Vollbild: Systemleisten ausblenden, Kamerabild bis in die Ecken
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        granted = hasCam()
        notifOn = notifEnabled()
        if (granted) startStreaming()
        val missing = startupPerms()
        if (missing.isNotEmpty()) permLauncher.launch(missing)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, onPrimary = OnAccent, background = Ink,
                surface = Ink, onSurface = TextHi, onBackground = TextHi)) {
                androidx.compose.material3.Surface(color = Color.Black, contentColor = TextHi, modifier = Modifier.fillMaxSize()) {
                    val s = svc
                    if (!granted || s == null) Waiting() else Main(s)
                }
            }
        }
    }

    override fun onDestroy() {
        svc?.let { s -> s.cam.onReady = null; s.cam.onError = null; s.server.onClientsChanged = null; s.onStopped = null }
        if (bound) { unbindService(conn); bound = false }
        super.onDestroy()
    }

    private fun applySaver(on: Boolean) {
        saver = on
        val lp = window.attributes
        lp.screenBrightness = if (on) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    private fun ip(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "<Handy-IP>"
    } catch (_: Exception) { "<Handy-IP>" }

    @Suppress("DEPRECATION", "UNUSED_PARAMETER")
    private fun configureTransform(tv: TextureView, version: Int) {
        val cam = svc?.cam ?: return
        val vw = tv.width.toFloat(); val vh = tv.height.toFloat()
        if (vw == 0f || vh == 0f || cam.previewW == 0) return
        val rotation = windowManager.defaultDisplay.rotation
        val m = Matrix()
        val viewRect = RectF(0f, 0f, vw, vh)
        val cx = viewRect.centerX(); val cy = viewRect.centerY()
        if (rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270) {
            val buf = RectF(0f, 0f, cam.previewH.toFloat(), cam.previewW.toFloat())
            buf.offset(cx - buf.centerX(), cy - buf.centerY())
            m.setRectToRect(viewRect, buf, Matrix.ScaleToFit.FILL)
            val scale = maxOf(vh / cam.previewH, vw / cam.previewW)
            m.postScale(scale, scale, cx, cy)
            m.postRotate(90f * (rotation - 2), cx, cy)
        } else if (rotation == android.view.Surface.ROTATION_180) {
            m.postRotate(180f, cx, cy)
        }
        tv.setTransform(m)
    }

    // ===================== Bildschirme =====================
    @Composable
    private fun Waiting() {
        Box(Modifier.fillMaxSize().background(Ink), Alignment.Center) {
            if (!granted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tr("cam_need"), color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("cam_need_sub"), color = TextLo, fontSize = 13.sp)
                    PrimaryButton(tr("grant")) { permLauncher.launch(perms()) }
                }
            } else Text(tr("starting"), color = TextLo)
        }
    }

    @Composable
    private fun Main(s: StreamService) {
        val cam = s.cam
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Kamerabild (Seitenverhältnis wie der Stream)
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Box(Modifier.aspectRatio(cam.width.toFloat().coerceAtLeast(1f) / cam.height.coerceAtLeast(1))) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
                                        svc?.cam?.attachPreview(t); configureTransform(tv, 0)
                                    }
                                    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) = configureTransform(tv, 0)
                                    override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
                                        val c = svc?.cam ?: return true
                                        c.detachPreview(t)   // Stream läuft weiter, Texture wird danach freigegeben
                                        return false
                                    }
                                    override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
                                }
                                tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> configureTransform(tv, 0) }
                            }
                        },
                        update = { tv -> configureTransform(tv, caps) }
                    )
                }
            }

            // Overlay innerhalb der sicheren Bereiche (Notch/Kamera-Loch)
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                StatusPill(cam, Modifier.align(Alignment.TopStart))
                Rail(cam, Modifier.align(Alignment.CenterEnd))
                if (panelOpen) {
                    Panel(s, Modifier.align(Alignment.CenterEnd).padding(end = 64.dp).width(372.dp).fillMaxHeight())
                }
                error?.let { msg ->
                    Row(Modifier.align(Alignment.BottomStart).widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xF2551B1B)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                        TextButton(onClick = { error = null }) { Text("OK", color = Color.White) }
                    }
                }
            }

            if (saver) {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable { applySaver(false) }, Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("saver"), color = TextLo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(tr("saver_overlay"), color = Color(0xFF59627A), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusPill(cam: CameraStreamer, modifier: Modifier) {
        val live = clients > 0
        Row(modifier.clip(RoundedCornerShape(50)).background(Color(0xB3000000))
            .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (live) Good else Color(0xFFF5B942)))
            Spacer(Modifier.width(8.dp))
            Text(if (live) tr("status_live") else tr("status_wait"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            val info = remember(caps) { "${cam.width}×${cam.height} · ${cam.fps} fps · ${cam.bitrateMbps} Mbit/s" }
            Text("   $info", color = Color(0xFFB8C2D4), fontSize = 12.sp)
        }
    }

    @Composable
    private fun Rail(cam: CameraStreamer, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailButton("⚙", panelOpen) { panelOpen = !panelOpen }
            RailButton("⟲", false) { cam.useFront = !cam.useFront; cam.restart() }
            RailButton("☀", torchOn) { torchOn = !torchOn; cam.torch = torchOn; cam.applyAsync() }
            RailButton("☾", false) { applySaver(true) }
        }
    }

    @Composable
    private fun RailButton(icon: String, active: Boolean, onClick: () -> Unit) {
        Box(Modifier.size(48.dp).clip(CircleShape)
            .background(if (active) Accent else Color(0xB3000000)).clickable(onClick = onClick), Alignment.Center) {
            Text(icon, color = if (active) OnAccent else Color.White, fontSize = 22.sp)
        }
    }

    // ===================== Panel =====================
    @Composable
    private fun Panel(s: StreamService, modifier: Modifier) {
        val cam = s.cam
        Column(modifier.clip(RoundedCornerShape(26.dp)).background(Glass)) {
            // Tabs
            Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Chip).padding(4.dp)) {
                TAB_KEYS.forEachIndexed { i, tk ->
                    val sel = i == tab
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Accent else Color.Transparent)
                        .clickable { tab = i }.padding(vertical = 9.dp, horizontal = 2.dp), Alignment.Center) {
                        Text(tr(tk), color = if (sel) OnAccent else TextLo, fontSize = 12.sp, maxLines = 1, softWrap = false,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                key(caps, tab) {
                    when (tab) {
                        0 -> ConnectTab(s)
                        1 -> VideoTab(cam)
                        2 -> ImageTab(cam)
                        3 -> SoundTab(cam)
                        else -> MoreTab()
                    }
                }
            }
        }
    }

    @Composable
    private fun ConnectTab(s: StreamService) {
        val ip = remember { ip() }
        val url = "http://$ip:8080/player.html"
        val clip = LocalClipboardManager.current
        var copied by remember { mutableStateOf(false) }

        Group {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (clients > 0) Good else Color(0xFFF5B942)))
                Spacer(Modifier.width(8.dp))
                Text(if (clients > 0) tr("conn_ok") else tr("conn_wait"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Group(tr("step1")) {
            Hint(tr("step1_hint"))
            SelectionContainer {
                Text(url, color = Accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            GhostButton(if (copied) tr("copied") else tr("copy")) { clip.setText(AnnotatedString(url)); copied = true }
        }
        Group(tr("step2")) {
            Hint(tr("step2_h1"))
            Hint(tr("step2_h2"))
        }
        Group(tr("pin_group")) {
            SwitchRow(tr("pin_switch"), tr("pin_switch_sub"), pinValue != null) { on ->
                PinStore.setEnabled(applicationContext, on)
                pinValue = PinStore.active(applicationContext)
                s.applyPinSettings()
            }
            val pv = pinValue
            if (pv != null) {
                Text(pv.chunked(3).joinToString(" "), color = Accent, fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                GhostButton(tr("pin_new")) {
                    PinStore.regenerate(applicationContext)
                    pinValue = PinStore.active(applicationContext)
                    s.applyPinSettings()
                    copied = false
                }
                Hint(tr("pin_hint"))
            } else {
                Hint(tr("pin_off_hint"))
            }
            Hint(tr("pin_note"))
        }
        Group(tr("rot_title")) {
            Pills(listOf("0°", "90°", "180°", "270°"), playerRot / 90) {
                playerRot = it * 90; s.server.setPlayerView(playerRot, playerMirror)
            }
            SwitchRow(tr("mirror"), tr("mirror_sub"), playerMirror) {
                playerMirror = it; s.server.setPlayerView(playerRot, it)
            }
            Hint(tr("rot_hint"))
        }
        Hint(tr("usb_hint"))
        OutlinedButtonDanger(tr("stop")) { s.shutdown() }
    }

    private fun sizeName(s: Size) = when {
        s.width >= 3840 -> "4K"
        s.width == 2560 -> "1440p"
        s.width == 1920 -> "1080p"
        s.width == 1280 -> "720p"
        else -> "${s.width}×${s.height}"
    }

    @Composable
    private fun VideoTab(cam: CameraStreamer) {
        Group(tr("camera")) {
            Pills(listOf(tr("rear"), tr("front")), if (cam.useFront) 1 else 0) { cam.useFront = it == 1; cam.restart() }
        }
        Group(tr("resolution")) {
            Pills(cam.sizes.map { sizeName(it) }, cam.sizes.indexOfFirst { it.width == cam.width && it.height == cam.height }) {
                cam.width = cam.sizes[it].width; cam.height = cam.sizes[it].height; cam.restart()
            }
        }
        Group(tr("framerate")) {
            Pills(cam.fpsList.map { "$it fps" }, cam.fpsList.indexOf(cam.fps)) { cam.fps = cam.fpsList[it]; cam.restart() }
        }
        Group(tr("bitrate")) {
            val rates = listOf(3, 6, 10, 16, 25)
            Pills(rates.map { "$it Mbit/s" }, rates.indexOf(cam.bitrateMbps)) { cam.bitrateMbps = rates[it]; cam.restart() }
            Hint(tr("bitrate_hint"))
        }
    }

    @Composable
    private fun ImageTab(cam: CameraStreamer) {
        if (cam.maxZoom > 1.01f) {
            Group(tr("zoom")) {
                var z by remember { mutableFloatStateOf(cam.zoom) }
                SliderRow(tr("magnification"), "%.1f×".format(z), z, 1f..cam.maxZoom) { z = it; cam.zoom = it; cam.applyAsync() }
            }
        }
        Group(tr("exposure")) {
            var manual by remember { mutableStateOf(cam.aeManual && cam.manualSensor) }
            var aeLocked by remember { mutableStateOf(cam.aeLock) }
            Pills(if (cam.manualSensor) listOf(tr("auto"), tr("manual")) else listOf(tr("auto")), if (manual) 1 else 0,
                enabled = !aeLocked) {
                manual = it == 1; cam.aeManual = manual; cam.applyAsync()
            }
            if (manual) {
                val lo = cam.isoRange.lower.toDouble(); val hi = cam.isoRange.upper.toDouble()
                var p by remember { mutableFloatStateOf(CameraStreamer.invLog(cam.iso.toDouble(), lo, hi)) }
                SliderRow(tr("iso"), "${CameraStreamer.logMap(p.toInt(), lo, hi).toInt()}", p, 0f..100f) {
                    p = it; cam.iso = CameraStreamer.logMap(it.toInt(), lo, hi).toInt(); cam.applyAsync()
                }
                val elo = cam.expRange.lower.toDouble(); val ehi = minOf(cam.expRange.upper, 100_000_000L).toDouble()
                var t by remember { mutableFloatStateOf(CameraStreamer.invLog(cam.exposureNs.toDouble().coerceIn(elo, ehi), elo, ehi)) }
                SliderRow(tr("shutter"), "1/${(1e9 / CameraStreamer.logMap(t.toInt(), elo, ehi)).toInt()} s", t, 0f..100f) {
                    t = it; cam.exposureNs = CameraStreamer.logMap(it.toInt(), elo, ehi).toLong(); cam.applyAsync()
                }
            } else {
                val span = cam.evRange.upper - cam.evRange.lower
                if (span > 0) {
                    var ev by remember { mutableFloatStateOf(cam.evIndex.toFloat()) }
                    SliderRow(tr("ev"), "%+.1f".format(ev * cam.evStep), ev,
                        cam.evRange.lower.toFloat()..cam.evRange.upper.toFloat(), span - 1, enabled = !aeLocked) {
                        ev = it; cam.evIndex = Math.round(it); cam.applyAsync()
                    }
                }
                SwitchRow(tr("ae_lock"), if (aeLocked) tr("locked") else tr("ae_lock_sub"), aeLocked) {
                    aeLocked = it; cam.aeLock = it; cam.applyAsync()
                }
            }
        }
        Group(tr("focus")) {
            var mf by remember { mutableStateOf(cam.afManual && cam.minFocus > 0f) }
            Pills(if (cam.minFocus > 0f) listOf(tr("auto"), tr("manual")) else listOf(tr("auto")), if (mf) 1 else 0) {
                mf = it == 1; cam.afManual = mf; cam.applyAsync()
            }
            if (mf) {
                var f by remember { mutableFloatStateOf(cam.focusDiopter / cam.minFocus) }
                SliderRow(tr("distance"), if (f < 0.01f) "∞" else "%.0f cm".format(100f / (f * cam.minFocus)), f, 0f..1f) {
                    f = it; cam.focusDiopter = it * cam.minFocus; cam.applyAsync()
                }
            }
        }
        Group(tr("wb")) {
            val names = listOf(tr("wb_auto"), tr("wb_daylight"), tr("wb_cloudy"), tr("wb_incandescent"), tr("wb_fluorescent"), tr("wb_shade"))
            val modes = listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, CaptureRequest.CONTROL_AWB_MODE_SHADE)
            var awb by remember { mutableIntStateOf(modes.indexOf(cam.awbMode).coerceAtLeast(0)) }
            var wbLocked by remember { mutableStateOf(cam.awbLock) }
            Pills(names, awb, enabled = !wbLocked) { awb = it; cam.awbMode = modes[it]; cam.applyAsync() }
            SwitchRow(tr("wb_lock"), if (wbLocked) tr("locked") else tr("wb_lock_sub"), wbLocked) {
                wbLocked = it; cam.awbLock = it; cam.applyAsync()
            }
        }
        Group {
            SwitchRow(tr("torch"), tr("torch_sub"), torchOn) { torchOn = it; cam.torch = it; cam.applyAsync() }
        }
    }

    @Composable
    private fun SoundTab(cam: CameraStreamer) {
        Group(tr("mic")) {
            var audio by remember { mutableStateOf(cam.audioOn) }
            SwitchRow(tr("audio_send"), tr("audio_sub"), audio) { want ->
                if (want && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permLauncher.launch(perms())
                    error = tr("audio_perm")
                } else { audio = want; cam.audioOn = want; svc?.refreshNotification(); cam.restart() }
            }
        }
    }

    @Composable
    private fun MoreTab() {
        Group(tr("energy")) {
            SwitchRow(tr("saver"), tr("saver_sub"), saver) { applySaver(it) }
            Hint(tr("energy_hint1"))
            Hint(tr("energy_hint2"))
        }
        Group(tr("notif_group")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (notifOn) Good else Bad))
                Spacer(Modifier.width(8.dp))
                Text(if (notifOn) tr("notif_on") else tr("notif_off"), color = Color.White, fontSize = 13.sp)
            }
            if (!notifOn) {
                Hint(tr("notif_off_hint"))
                GhostButton(tr("notif_open")) {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }
        }
        Group(tr("language")) {
            Pills(I18n.names, I18n.index()) { I18n.set(this@MainActivity, I18n.codes[it]) }
        }
    }
}

// ===================== UI-Bausteine =====================
@Composable
private fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Chip).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) Text(title, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = TextLo, fontSize = 12.sp, lineHeight = 16.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    FlowRow(Modifier.alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(Modifier.clip(RoundedCornerShape(50))
                .background(if (sel) Accent else Color(0x1AFFFFFF))
                .clickable(enabled = enabled) { onSelect(i) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(o, color = if (sel) OnAccent else Color(0xFFD5DCEA), fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
                      steps: Int = 0, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column(Modifier.alpha(if (enabled) 1f else 0.4f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = TextHi)
            Text(valueText, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent,
                inactiveTrackColor = Color(0x33FFFFFF), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                disabledThumbColor = Accent, disabledActiveTrackColor = Accent, disabledInactiveTrackColor = Color(0x33FFFFFF)))
    }
}

@Composable
private fun SwitchRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = TextHi)
            if (sub != null) Text(sub, color = TextLo, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent,
                uncheckedTrackColor = Color(0x33FFFFFF), uncheckedThumbColor = Color(0xFFB8C2D4), uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Accent).clickable(onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 11.dp)) {
        Text(text, color = OnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x26FFFFFF)).clickable(onClick = onClick)
        .padding(vertical = 10.dp), Alignment.Center) {
        Text(text, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OutlinedButtonDanger(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1FFF6B6B)).clickable(onClick = onClick)
        .padding(vertical = 12.dp), Alignment.Center) {
        Text(text, color = Bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
