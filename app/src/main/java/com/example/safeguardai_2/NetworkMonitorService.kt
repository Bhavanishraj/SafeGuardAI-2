package com.example.safeguardai_2

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class NetworkMonitorService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var isLowNetworkActive = false
    private val LOW_SIGNAL_THRESHOLD = 1 // 0 or 1 is very poor signal

    override fun onCreate() {
        super.onCreate()
        startForeground(2, createNotification())
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        registerSignalListener()
    }

    private fun registerSignalListener() {
        // For Android 12 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(mainExecutor, object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    checkSignalAndNotify(signalStrength.level)
                }
            })
        } else {
            // For older versions (Legacy Support)
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    // level is hidden in older APIs, we calculate it or use a default
                    val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) signalStrength.level else 2
                    checkSignalAndNotify(level)
                }
            }
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    private fun checkSignalAndNotify(level: Int) {
        Log.d("NetworkMonitor", "Current Signal Level: $level")

        // Threshold Logic
        if (level <= LOW_SIGNAL_THRESHOLD && !isLowNetworkActive) {
            isLowNetworkActive = true
            sendNetworkAlert("⚠️ Entering Low Network Zone. Last known location:")
        } else if (level > (LOW_SIGNAL_THRESHOLD + 1) && isLowNetworkActive) {
            // We use (threshold + 1) to avoid "flapping" if the signal is flickering
            isLowNetworkActive = false
            sendNetworkAlert("✅ Leaving Low Network Zone. Current location:")
        }
    }

    private fun sendNetworkAlert(status: String) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        // Request a FRESH location rather than just the "last" location
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val mapsLink = "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}"
                    val message = "$status $mapsLink"

                    val prefs = getSharedPreferences("SOS_Prefs", MODE_PRIVATE)
                    val phone = prefs.getString("selected_phone", "") ?: ""

                    if (phone.isNotEmpty()) {
                        try {
                            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                this.getSystemService(SmsManager::class.java)
                            } else { SmsManager.getDefault() }

                            smsManager.sendTextMessage(phone, null, message, null, null)
                            Log.d("NetworkMonitor", "Alert Sent: $status")
                        } catch (e: Exception) {
                            Log.e("NetworkMonitor", "SMS Failed: ${e.message}")
                        }
                    }
                }
            }
    }

    private fun createNotification(): Notification {
        val channelId = "NetworkMonitorChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Network Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Network Guard Active")
            .setContentText("Monitoring signal for safety alerts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}