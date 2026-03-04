package com.example.safeguardai_2

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.util.Calendar
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var etPhone: EditText
    private lateinit var tvRiskStatus: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var tvSoundValue: TextView
    private lateinit var llRiskStatus: LinearLayout
    private lateinit var pbHeartRate: ProgressBar

    private lateinit var switchShake: SwitchCompat
    private lateinit var switchIoT: SwitchCompat
    private lateinit var switchNetworkGuard: SwitchCompat
    private lateinit var switchVolumeTrigger: SwitchCompat
    private lateinit var cbSendAll: CheckBox

    private lateinit var crimePredictor: CrimePredictor
    private lateinit var sensorManager: SensorManager

    private var lastSmsTime: Long = 0
    private var isManualTrigger = false

    // IoT Watchdog
    private val disconnectionHandler = Handler(Looper.getMainLooper())
    private var isDeviceConnected = false
    private val DISCONNECT_TIMEOUT = 10000L

    private var accelCurrent = SensorManager.GRAVITY_EARTH
    private var accelLast = SensorManager.GRAVITY_EARTH
    private var accelMagnitude = 0f

    private val checkDisconnection = Runnable {
        if (isDeviceConnected) {
            isDeviceConnected = false
            Toast.makeText(this, "❌ IoT Device Disconnected", Toast.LENGTH_LONG).show()
            tvHeartRate.text = "💓 BPM: --"
            tvSoundValue.text = "🔊 Sound: --"
            pbHeartRate.progress = 0
        }
    }

    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val phone = result.data?.getStringExtra("selected_phone")
            if (phone != null) etPhone.setText(phone)
        }
    }

    private val esp32Receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!switchIoT.isChecked) return

            when (intent?.action) {
                "ESP32_DATA" -> {
                    val rawData = intent.getStringExtra("raw") ?: ""
                    if (!isDeviceConnected) {
                        isDeviceConnected = true
                        Toast.makeText(context, "✅ IoT Device Connected", Toast.LENGTH_SHORT).show()
                    }
                    disconnectionHandler.removeCallbacks(checkDisconnection)
                    disconnectionHandler.postDelayed(checkDisconnection, DISCONNECT_TIMEOUT)
                    processIoTData(rawData)
                }
                "ESP32_EMERGENCY" -> triggerEmergency("Hardware Panic Button")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        crimePredictor = CrimePredictor(this)

        etPhone = findViewById(R.id.etPhone)
        tvRiskStatus = findViewById(R.id.tvRiskStatus)
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvSoundValue = findViewById(R.id.tvSoundValue)
        llRiskStatus = findViewById(R.id.llRiskStatus)
        pbHeartRate = findViewById(R.id.pbHeartRate)
        switchShake = findViewById(R.id.switchShake)
        switchIoT = findViewById(R.id.switchIoT)
        switchNetworkGuard = findViewById(R.id.switchNetworkGuard)
        switchVolumeTrigger = findViewById(R.id.switchVolumeTrigger)
        cbSendAll = findViewById(R.id.cbSendAll)

        createNotificationChannel()
        checkOverlayPermission()

        startService(Intent(this, BluetoothService::class.java))

        switchVolumeTrigger.setOnCheckedChangeListener { _, isChecked ->
            val vIntent = Intent(this, VolumeButtonService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(vIntent)
                else startService(vIntent)
            } else stopService(vIntent)
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            isManualTrigger = true
            triggerEmergency("Manual Trigger")
        }

        findViewById<View>(R.id.btnViewContacts).setOnClickListener {
            pickContactLauncher.launch(Intent(this, ContactsActivity::class.java))
        }

        runMLPrediction()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun processIoTData(raw: String) {
        runOnUiThread {
            try {
                // Expected: "BPM:85,SOUND:300"
                val parts = raw.split(",")
                var bpm = 0; var sound = 0
                for (part in parts) {
                    val clean = part.trim()
                    if (clean.contains("BPM:")) bpm = clean.substringAfter(":").toIntOrNull() ?: 0
                    if (clean.contains("SOUND:")) sound = clean.substringAfter(":").toIntOrNull() ?: 0
                }

                tvHeartRate.text = "💓 BPM: $bpm"
                tvSoundValue.text = "🔊 Sound: $sound"
                pbHeartRate.progress = bpm

                if (bpm > 125 || sound > 900) {
                    isManualTrigger = false
                    triggerEmergency("Sensor Anomaly Detected")
                }
            } catch (e: Exception) { Log.e("DataErr", "Parsing error: $raw") }
        }
    }

    override fun onSensorChanged(e: SensorEvent?) {
        if (!switchShake.isChecked || e?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        accelLast = accelCurrent
        accelCurrent = sqrt((e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]).toDouble()).toFloat()
        accelMagnitude = accelMagnitude * 0.9f + (accelCurrent - accelLast)
        if (accelMagnitude > 12f) {
            isManualTrigger = false
            triggerEmergency("Shake Detected")
        }
    }

    private fun triggerEmergency(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSmsTime > 30000) {
            lastSmsTime = now
            runOnUiThread {
                Toast.makeText(this, "🚨 SOS: $reason", Toast.LENGTH_SHORT).show()
                handleSOSFlow()
            }
        }
    }

    private fun handleSOSFlow() {
        val perms = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            requestLocationAndSendSOS()
        } else {
            requestPermissionLauncher.launch(perms)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) requestLocationAndSendSOS()
    }

    @RequiresPermission(allOf = [Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun requestLocationAndSendSOS() {
        val phone = etPhone.text.toString().trim()
        if (phone.isEmpty()) return
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
            sendEmergencyPayload(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0, isManualTrigger, phone)
        }
    }

    private fun sendEmergencyPayload(lat: Double, lon: Double, manual: Boolean, phone: String) {
        val mapsLink = if (lat != 0.0) "https://www.google.com/maps?q=$lat,$lon" else "GPS Unavailable"
        val message = "🚨 SOS! I need help. My Location: $mapsLink"
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        try {
            smsManager.sendMultipartTextMessage(phone, null, smsManager.divideMessage(message), null, null)
            if (manual) startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply { putExtra("sms_body", message) })
        }
    }

    private fun runMLPrediction() {
        val score = crimePredictor.getRiskScore(1, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        runOnUiThread {
            if (score > 0.6) {
                llRiskStatus.setBackgroundColor(Color.parseColor("#FFCDD2"))
                tvRiskStatus.text = "Status: High Risk Area"
            } else {
                llRiskStatus.setBackgroundColor(Color.parseColor("#E1F5FE"))
                tvRiskStatus.text = "Status: Safe Area"
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("BT_CHANNEL", "SafeGuard Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        val filter = IntentFilter().apply {
            addAction("ESP32_DATA")
            addAction("ESP32_EMERGENCY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(esp32Receiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(esp32Receiver, filter)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        try { unregisterReceiver(esp32Receiver) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        disconnectionHandler.removeCallbacks(checkDisconnection)
        super.onDestroy()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
}