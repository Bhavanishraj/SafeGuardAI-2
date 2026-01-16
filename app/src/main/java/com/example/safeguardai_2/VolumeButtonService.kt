package com.example.safeguardai_2

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VolumeButtonService : Service() {
    private var count = 0
    private var lastTime: Long = 0

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTime < 2000) {
                    count++
                } else {
                    count = 1
                }
                lastTime = currentTime

                if (count >= 3) {
                    count = 0
                    val callIntent = Intent(context, FakeCallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    context?.startActivity(callIntent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(volumeReceiver)
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "VolumeServiceChannel"

        // Fix for API 26+ error: Must create a channel for newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Safety Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeGuard AI Active")
            .setContentText("Listening for volume triggers")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}