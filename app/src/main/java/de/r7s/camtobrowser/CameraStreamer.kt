package de.r7s.camtobrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Kamera + Hardware-H.264-Encoder. Läuft im Dienst und ist unabhängig von der Vorschau:
 * Die Vorschau wird nur bei sichtbarer Oberfläche dazugeschaltet (attach/detachPreview).
 */
class CameraStreamer(ctx: Context, private val server: StreamServer) {
    private val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("cam").apply { start() }
    private val handler = Handler(thread.looper)

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewTex: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var targets: List<Surface> = emptyList()
    private var enc: VideoEncoder? = null
    private var audioEnc: AudioEncoder? = null
    private var chars: CameraCharacteristics? = null

    // ---- Einstellungen ----
    var bitrateMbps = 6
    var audioOn = false
    var width = 1920
    var height = 1080
    var fps = 30
    var useFront = false
    var aeManual = false
    var iso = 400
    var exposureNs = 10_000_000L
    var evIndex = 0
    var afManual = false
    var focusDiopter = 0f
    var awbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    var aeLock = false
    var awbLock = false
    var zoom = 1f
    var torch = false

    // ---- Fähigkeiten ----
    var sizes: List<Size> = emptyList(); private set
    var fpsList: List<Int> = listOf(30); private set
    var isoRange: Range<Int> = Range(100, 800); private set
    var expRange: Range<Long> = Range(1_000_000L, 33_000_000L); private set
    var evRange: Range<Int> = Range(0, 0); private set
    var evStep = 0f; private set
    var minFocus = 0f; private set
    var maxZoom = 1f; private set
    var manualSensor = false; private set
    var previewW = 0; private set
    var previewH = 0; private set
    var onReady: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private fun pickId(): String = manager.cameraIdList.first {
        val f = manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)
        f == if (useFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
    }

    private fun is169(s: Size) = abs(s.width.toDouble() / s.height - 16.0 / 9) < 0.02

    fun start() { handler.post { startInternal() } }
    fun restart() = start()
    fun stop() { handler.post { closeInternal() } }
    fun release() { handler.post { closeInternal(); thread.quitSafely() } }

    // ---------- Vorschau an-/abschalten (Encoder läuft weiter) ----------
    fun attachPreview(tex: SurfaceTexture) {
        handler.post {
            previewTex = tex
            if (previewW > 0) tex.setDefaultBufferSize(previewW, previewH)
            previewSurface = Surface(tex)
            buildSession()
        }
    }

    fun detachPreview(tex: SurfaceTexture) {
        handler.post {
            val ps = previewSurface
            previewSurface = null; previewTex = null
            buildSession {
                try { ps?.release() } catch (_: Exception) {}
                try { tex.release() } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startInternal() {
        closeInternal()
        try {
            val id = try { pickId() } catch (_: Exception) { manager.cameraIdList.first() }
            val c = manager.getCameraCharacteristics(id)
            chars = c
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

            val rec = map.getOutputSizes(MediaRecorder::class.java)?.toList() ?: emptyList()
            val all = rec.filter { it.width in 640..3840 && it.height <= 2160 }
            // Nur gaengige 16:9-Standardgroessen anbieten (keine Dubletten wie 1920x1088).
            val std = listOf(3840 to 2160, 2560 to 1440, 1920 to 1080, 1280 to 720, 960 to 540, 854 to 480, 640 to 360)
            val clean = std.mapNotNull { (w, h) -> all.firstOrNull { it.width == w && it.height == h } }
            val base = if (clean.isNotEmpty()) clean
                else (all.filter { is169(it) }.ifEmpty { all }).distinctBy { it.width }
            sizes = base.sortedByDescending { it.width * it.height }
            if (sizes.none { it.width == width && it.height == height }) {
                val s = sizes.minByOrNull { abs(it.width - 1920) } ?: Size(1280, 720)
                width = s.width; height = s.height
            }
            val size = Size(width, height)

            val ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
            val minDur = map.getOutputMinFrameDuration(MediaRecorder::class.java, size)
            val maxFps = if (minDur > 0) 1e9 / minDur + 0.5 else 60.0
            fpsList = listOf(15, 24, 30, 60).filter { f -> f <= maxFps && ranges.any { f in it.lower..it.upper } }
                .ifEmpty { listOf(30) }
            if (fps !in fpsList) fps = if (30 in fpsList) 30 else fpsList.last()

            isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: isoRange
            expRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: expRange
            evRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: evRange
            evStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f
            minFocus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            manualSensor = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
            iso = iso.coerceIn(isoRange.lower, isoRange.upper)
            exposureNs = exposureNs.coerceIn(expRange.lower, expRange.upper)
            evIndex = evIndex.coerceIn(evRange.lower, evRange.upper)
            zoom = zoom.coerceIn(1f, maxZoom)

            // Vorschau klein halten (spart GPU/Wärme)
            val prevs = map.getOutputSizes(SurfaceTexture::class.java).toList()
            val prev = prevs.filter { abs(it.width.toDouble() / it.height - width.toDouble() / height) < 0.02 && it.width <= 960 }
                .maxByOrNull { it.width * it.height } ?: prevs.minByOrNull { abs(it.width - 960) }!!
            previewW = prev.width; previewH = prev.height
            previewTex?.let { it.setDefaultBufferSize(previewW, previewH); previewSurface = Surface(it) }

            server.videoW = width; server.videoH = height; server.videoFps = fps
            val e = try {
                VideoEncoder(width, height, fps, bitrateMbps * 1_000_000) { data, key -> server.pushVideo(data, key) }
            } catch (ex: Exception) {
                onError?.invoke(tr("err_h264", width, height, fps, ex.message ?: ""))
                onReady?.invoke(); return
            }
            enc = e
            server.onNeedKeyFrame = { enc?.requestKeyFrame() }
            if (audioOn) {
                try { audioEnc = AudioEncoder { server.pushPcm(it) }.also { it.start() } }
                catch (ex: Exception) { onError?.invoke(tr("err_audio", ex.message ?: "")); audioEnc = null }
            }
            onReady?.invoke()

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) { camera = cam; buildSession() }
                override fun onDisconnected(cam: CameraDevice) { cam.close() }
                override fun onError(cam: CameraDevice, e: Int) { cam.close(); onError?.invoke(tr("err_cam", e.toString())) }
            }, handler)
        } catch (ex: Exception) {
            onError?.invoke(tr("err_cam", ex.message ?: ""))
        }
    }

    /** (Neu-)Aufbau der Capture-Session mit den aktuell vorhandenen Zielen. */
    private fun buildSession(onDone: (() -> Unit)? = null) {
        val cam = camera
        if (cam == null) { onDone?.invoke(); return }
        val list = ArrayList<Surface>()
        previewSurface?.let { list.add(it) }
        enc?.let { list.add(it.surface) }
        targets = list
        session = null
        try {
            cam.createCaptureSession(list, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { session = s; apply(); onDone?.invoke() }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    onError?.invoke(tr("err_camcfg", width, height, fps))
                    onDone?.invoke()
                }
            }, handler)
        } catch (e: Exception) { onError?.invoke(tr("err_cam", e.message ?: "")); onDone?.invoke() }
    }

    private fun apply() {
        val cam = camera ?: return
        val s = session ?: return
        val c = chars ?: return
        try {
            val b = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            for (t in targets) b.addTarget(t)
            b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            val ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
            ranges.filter { fps in it.lower..it.upper }.minByOrNull { it.upper - it.lower }
                ?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }

            if (aeManual && manualSensor) {
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                b.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
                b.set(CaptureRequest.SENSOR_FRAME_DURATION, max(1_000_000_000L / fps, exposureNs))
            } else {
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIndex)
                b.set(CaptureRequest.CONTROL_AE_LOCK, aeLock)
            }
            if (afManual && minFocus > 0f) {
                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopter)
            } else {
                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }
            b.set(CaptureRequest.CONTROL_AWB_MODE, awbMode)
            b.set(CaptureRequest.CONTROL_AWB_LOCK, awbLock)
            val r = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
            val cw = (r.width() / zoom).toInt(); val ch = (r.height() / zoom).toInt()
            b.set(CaptureRequest.SCALER_CROP_REGION, Rect(r.centerX() - cw / 2, r.centerY() - ch / 2, r.centerX() + cw / 2, r.centerY() + ch / 2))
            b.set(CaptureRequest.FLASH_MODE, if (torch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            s.setRepeatingRequest(b.build(), null, handler)
        } catch (_: Exception) {}
    }

    /** Einstellungen anwenden, ohne die UI zu blockieren. */
    fun applyAsync() { handler.post { apply() } }

    private fun closeInternal() {
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { previewSurface?.release() } catch (_: Exception) {}
        val hadEnc = enc != null
        enc?.stop(); audioEnc?.stop()
        if (hadEnc) server.resetClients()
        session = null; camera = null; previewSurface = null; enc = null; audioEnc = null
    }

    companion object {
        fun logMap(p: Int, lo: Double, hi: Double) = lo * (hi / lo).pow(p / 100.0)
        fun invLog(v: Double, lo: Double, hi: Double): Float =
            (Math.log(v / lo) / Math.log(hi / lo) * 100).toFloat().coerceIn(0f, 100f)
    }
}
