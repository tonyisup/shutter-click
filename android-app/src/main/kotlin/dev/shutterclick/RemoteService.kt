package dev.shutterclick

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

class RemoteService : Service() {
    companion object { const val STOP = "dev.shutterclick.STOP"; private const val CHANNEL = "remote" }
    private val main = Handler(Looper.getMainLooper())
    private val remote get() = (application as ShutterApplication).remote
    private val expiryCheck = object : Runnable {
        override fun run() {
            if (!remote.engine.active) { stopSelf(); return }
            main.postDelayed(this, 1000)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (remote.running) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            CameraControlService.current == null || !remote.garmin.connected || !remote.garmin.appInstalled) {
            stopSelf(); return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Camera remote", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, RemoteService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shutter)
            .setContentTitle("Camera remote active")
            .setContentText("Open Pixel Camera in Photo mode. START takes a photo.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "End remote", stop).build()).build()
        try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            remote.begin()
            main.post(expiryCheck)
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to start remote. Check phone setup.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        main.removeCallbacks(expiryCheck)
        remote.end()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
