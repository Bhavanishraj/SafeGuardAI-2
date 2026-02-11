package com.example.safeguardai_2

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.telephony.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices

class NetworkMonitorService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var isLowNetworkActive = false
    private val LOW_SIGNAL_THRESHOLD = 2 // Level ranges 0 (none) to 4 (excellent)

    override fun onCreate() {
        super.onCreate()
        startForeground(2, createNotification())
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Listen for signal strength changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(mainExecutor, object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    checkSignalAndNotify(signalStrength.level)
                }
            })
        }
    }

    private fun checkSignalAndNotify(level: Int) {
        if (level <= LOW_SIGNAL_THRESHOLD && !isLowNetworkActive) {
            isLowNetworkActive = true
            sendNetworkAlert("⚠️ Entering Low Network Zone. Last location:")
        } else if (level > LOW_SIGNAL_THRESHOLD && isLowNetworkActive) {
            isLowNetworkActive = false
            sendNetworkAlert("✅ Leaving Low Network Zone. Current location:")
        }
    }

    private fun sendNetworkAlert(status: String) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val mapsLink = "http://maps.google.com/maps?q=${loc.latitude},${loc.longitude}"
                    val message = "$status $mapsLink"

                    // Retrieve contacts from SharedPreferences
                    val prefs = getSharedPreferences("SOS_Prefs", MODE_PRIVATE)
                    val phone = prefs.getString("selected_phone", "") ?: ""

                    if (phone.isNotEmpty()) {
                        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            this.getSystemService(SmsManager::class.java)
                        } else { SmsManager.getDefault() }

                        smsManager.sendTextMessage(phone, null, message, null, null)
                    }
                }
            }
        } catch (e: SecurityException) { /* Handle missing permissions */ }
    }

    private fun createNotification(): Notification {
        val channelId = "NetworkMonitorChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Network Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Network Guard Active")
            .setContentText("Monitoring signal for safety alerts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}