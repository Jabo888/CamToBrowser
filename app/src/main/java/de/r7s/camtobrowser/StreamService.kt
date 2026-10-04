package de.r7s.camtobrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.Manifest

/** Vordergrunddienst: Kamera, Encoder und Server laufen auch bei ausgeschaltetem Display weiter. */
@Suppress("DEPRECATION")
class StreamService : Service() {
    inner class LocalBinder : Binder() { fun get(): StreamService = this@StreamService }
    private val binder = LocalBinder()

    val server = StreamServer(8080)
    lateinit var cam: CameraStreamer
    @Volatile var running = false; private set
    var onStopped: (() -> Unit)? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        I18n.init(applicationContext)
        server.pin = PinStore.active(applicationContext)
        cam = CameraStreamer(applicationContext, server)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        goForeground()
        if (!running) {
            running = true
            acquireLocks()
            server.start()
            cam.start()
        }
        return START_NOT_STICKY
    }

    /** Benachrichtigung erneut einstellen, z. B. nachdem die Berechtigung erteilt wurde. */
    /** PIN-Einstellung aus dem Speicher übernehmen; verbundene Player werden getrennt. */
    fun applyPinSettings() {
        server.pin = PinStore.active(applicationContext)
        server.kickClients()
    }

    fun refreshNotification() { if (running) goForeground() }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("notif_title"), NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 1, Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val nb = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(tr("notif_title"))
            .setContentText(tr("notif_text"))
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, tr("notif_stop"), stopPi)
        if (Build.VERSION.SDK_INT >= 31) nb.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        val n = nb.build()
        if (Build.VERSION.SDK_INT >= 30) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIF_ID, n, types)
        } else startForeground(NOTIF_ID, n)
    }

    private fun acquireLocks() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camtobrowser:stream").apply { setReferenceCounted(false); acquire() }
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifi = wm.createWifiLock(
            if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "camtobrowser:wifi").apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseLocks() {
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) {}
        try { if (wifi?.isHeld == true) wifi?.release() } catch (_: Exception) {}
    }

    fun shutdown() {
        running = false
        cam.stop()
        server.stop()
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Handler(Looper.getMainLooper()).post { onStopped?.invoke() }
    }

    override fun onDestroy() {
        running = false
        cam.release()
        server.stop()
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "de.r7s.camtobrowser.STOP"
        private const val CHANNEL = "stream"
        private const val NOTIF_ID = 1
    }
}
