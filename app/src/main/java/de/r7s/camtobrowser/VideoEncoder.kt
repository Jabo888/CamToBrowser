package de.r7s.camtobrowser

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.Surface

/** Hardware-H.264-Encoder mit Surface-Eingang (Kamera schreibt direkt hinein -> kaum CPU-Last). */
class VideoEncoder(width: Int, height: Int, fps: Int, bitrate: Int,
                   private val onFrame: (ByteArray, Boolean) -> Unit) {
    private val codec: MediaCodec
    val surface: Surface
    @Volatile private var running = true
    private var config: ByteArray? = null
    private val worker: Thread
    private val aud = byteArrayOf(0, 0, 0, 1, 9, 0xF0.toByte())

    init {
        fun format(cbr: Boolean) = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE,
                if (cbr) MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR else MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            if (Build.VERSION.SDK_INT >= 23) setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            if (Build.VERSION.SDK_INT >= 30) { setInteger(MediaFormat.KEY_LOW_LATENCY, 1); setInteger(MediaFormat.KEY_LATENCY, 1) }
        }
        var c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            c.configure(format(true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            c.release()
            c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format(false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        codec = c
        surface = codec.createInputSurface()
        codec.start()
        worker = Thread { drain() }.apply { isDaemon = true; start() }
    }

    private fun drain() {
        val info = MediaCodec.BufferInfo()
        while (running) {
            val i = try { codec.dequeueOutputBuffer(info, 100_000) } catch (_: Exception) { break }
            if (i < 0) continue
            try {
                val buf = codec.getOutputBuffer(i)
                if (buf != null && info.size > 0) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val data = ByteArray(info.size); buf.get(data)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        config = data
                    } else {
                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val cfg = config
                        val body = if (key && cfg != null) aud + cfg + data else aud + data
                        onFrame(body, key)
                    }
                }
            } finally { try { codec.releaseOutputBuffer(i, false) } catch (_: Exception) {} }
        }
    }

    fun requestKeyFrame() {
        try { codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) } catch (_: Exception) {}
    }

    fun stop() {
        running = false
        try { worker.join(500) } catch (_: Exception) {}
        try { codec.stop() } catch (_: Exception) {}
        try { codec.release() } catch (_: Exception) {}
        try { surface.release() } catch (_: Exception) {}
    }
}
