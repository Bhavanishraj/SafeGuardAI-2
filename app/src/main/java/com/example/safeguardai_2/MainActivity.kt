package com.example.safeguardai_2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.KeyEvent
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var etPhone: EditText
    private lateinit var cbSendAll: CheckBox
    private lateinit var switchShake: SwitchCompat
    private lateinit var switchVolumeTrigger: SwitchCompat
    private lateinit var switchNetworkGuard: SwitchCompat // New: Network Guard Toggle

    // UI Elements for AI Monitor
    private lateinit var tvRiskStatus: TextView
    private lateinit var llRiskStatus: LinearLayout

    // ML Predictor
    private lateinit var crimePredictor: CrimePredictor

    // Shake detection
    private lateinit var sensorManager: SensorManager
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeCount = 0
    private var lastShakeTime: Long = 0

    // Volume trigger
    private var volumeBtnCount = 0
    private var lastVolumeBtnTime: Long = 0

    // Updated Permission Launcher to include CALL_PHONE and READ_PHONE_STATE
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.SEND_SMS] ?: false
        val callGranted = results[Manifest.permission.CALL_PHONE] ?: false
        val locGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (smsGranted && callGranted && locGranted) {
            executeFullSOS()
        } else {
            Toast.makeText(this, "Permissions (SMS, Call, Location) are required for SOS", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ML Predictor
        crimePredictor = CrimePredictor(this)

        // Initialize UI
        etPhone = findViewById(R.id.etPhone)
        cbSendAll = findViewById(R.id.cbSendAll)
        switchShake = findViewById(R.id.switchShake)
        switchVolumeTrigger = findViewById(R.id.switchVolumeTrigger)
        switchNetworkGuard = findViewById(R.id.switchNetworkGuard) // Initialize New Toggle
        tvRiskStatus = findViewById(R.id.tvRiskStatus)
        llRiskStatus = findViewById(R.id.llRiskStatus)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        // Button Listeners
        findViewById<Button>(R.id.btnViewContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            checkPermissionsAndTrigger()
        }

        // Toggle for Fake Call Background Service
        switchVolumeTrigger.setOnCheckedChangeListener { _, isChecked ->
            val serviceIntent = Intent(this, VolumeButtonService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                stopService(serviceIntent)
            }
        }

        // New: Toggle for Network Boundary Monitoring Service
        switchNetworkGuard.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(this, NetworkMonitorService::class.java)
            if (isChecked) {
                // Request Phone State permission before starting
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
        }
    }

    private fun checkPermissionsAndTrigger() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermissionLauncher.launch(permissions)
    }

    // Combined SOS Action: Sends SMS and starts a Call
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS])
    private fun executeFullSOS() {
        sendSOS() // Send SMS with Location

        val number = etPhone.text.toString().trim()
        if (number.isNotEmpty()) {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$number")
            try {
                startActivity(callIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun sendSOS() {
        val prefs = getSharedPreferences("SOS_Prefs", MODE_PRIVATE)
        val json = prefs.getString("contact_list", null)
        val type = object : TypeToken<MutableList<Contact>>() {}.type
        val contactsList: List<Contact> = Gson().fromJson(json, type) ?: emptyList()

        val recipients = mutableListOf<String>()
        if (cbSendAll.isChecked) {
            recipients.addAll(contactsList.map { it.phone })
        } else if (etPhone.text.isNotEmpty()) {
            recipients.add(etPhone.text.toString().trim())
        }

        if (recipients.isEmpty()) {
            Toast.makeText(this, "No recipients found.", Toast.LENGTH_SHORT).show()
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                // Fixed mapsLink template syntax
                val mapsLink = "https://www.google.com/maps?q=$/${loc.latitude},${loc.longitude}"
                val msg = "🚨 SOS ALERT! I need help. My location: $mapsLink"

                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        this.getSystemService(SmsManager::class.java)
                    } else {
                        SmsManager.getDefault()
                    }
                    recipients.forEach { num ->
                        val parts = smsManager.divideMessage(msg)
                        smsManager.sendMultipartTextMessage(num, null, parts, null, null)
                    }
                    Toast.makeText(this, "SOS Sent!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "SMS Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ... (runRiskAnalysis and updateRiskUI stay the same) ...

    override fun onSensorChanged(event: SensorEvent?) {
        if (!switchShake.isChecked) return
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            if (acceleration > 12) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime < 500) {
                    shakeCount++
                } else {
                    shakeCount = 1
                }
                lastShakeTime = now

                if (shakeCount >= 3) {
                    shakeCount = 0
                    checkPermissionsAndTrigger()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeBtnTime < 2000) {
                volumeBtnCount++
            } else {
                volumeBtnCount = 1
            }
            lastVolumeBtnTime = currentTime

            if (volumeBtnCount >= 3) {
                volumeBtnCount = 0
                startActivity(Intent(this, FakeCallActivity::class.java))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // ... (Existing SharedPreferences and Sensor Registration) ...
        val prefs = getSharedPreferences("SOS_Prefs", MODE_PRIVATE)
        val selectedPhone = prefs.getString("selected_phone", "")
        if (!selectedPhone.isNullOrEmpty()) {
            etPhone.setText(selectedPhone)
            prefs.edit().remove("selected_phone").apply()
        }

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runRiskAnalysis()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Manual inclusion of previous methods to ensure complete code
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun runRiskAnalysis() {
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val districtId = (Math.abs(loc.latitude.toInt() + loc.longitude.toInt()) % 50) + 1
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val riskScore = crimePredictor.getRiskScore(districtId, hour)
                updateRiskUI(riskScore)
            }
        }
    }

    private fun updateRiskUI(score: Float) {
        val percentage = (score * 100).toInt()
        tvRiskStatus.text = "Area Risk Level: $percentage%"
        if (score > 0.70) {
            llRiskStatus.setBackgroundColor(Color.parseColor("#FFCDD2"))
            tvRiskStatus.setTextColor(Color.parseColor("#B71C1C"))
        } else {
            llRiskStatus.setBackgroundColor(Color.parseColor("#E1F5FE"))
            tvRiskStatus.setTextColor(Color.parseColor("#01579B"))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}