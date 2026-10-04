package de.r7s.camtobrowser

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max

/** Mikrofon -> PCM 48 kHz / 16 Bit / Mono in 21-ms-Bloecken. */
class AudioEncoder(private val onPcm: (ByteArray) -> Unit) {
    private val rate = 48000
    @Volatile private var running = false
    private var worker: Thread? = null
    private var rec: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192) * 2)
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); throw IllegalStateException("Mikrofon nicht verfügbar") }
        rec = r; running = true
        r.startRecording()
        worker = Thread {
            val buf = ByteArray(2048)
            try {
                while (running) {
                    var got = 0
                    while (got < buf.size && running) {
                        val n = r.read(buf, got, buf.size - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got == buf.size) onPcm(buf.copyOf())
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { worker?.join(500) } catch (_: Exception) {}
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
    }
}
