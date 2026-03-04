package com.example.safeguardai_2

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VolumeButtonService : Service() {
    private var count = 0
    private var lastTime: Long = 0
    private var lastVolume = -1

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val newVolume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)

                // Check for Volume UP
                if (newVolume > lastVolume && lastVolume != -1) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTime < 2000) {
                        count++
                    } else {
                        count = 1
                    }
                    lastTime = currentTime

                    if (count >= 3) {
                        count = 0
                        // FAKE CALL TRIGGERED ONLY HERE
                        val callIntent = Intent(context, FakeCallActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        context?.startActivity(callIntent)
                    }
                }
                lastVolume = newVolume
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(101, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(volumeReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "VolumeServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Fake Call Listener", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Fake Call Armed")
            .setContentText("Tap Volume Up 3x to trigger")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}