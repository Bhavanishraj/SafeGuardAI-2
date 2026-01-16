package com.example.safeguardai_2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.KeyEvent
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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

    // Main SOS Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            sendSOS()
        } else {
            Toast.makeText(this, "Permissions required for SOS", Toast.LENGTH_SHORT).show()
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
    }

    private fun checkPermissionsAndTrigger() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermissionLauncher.launch(permissions)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun runRiskAnalysis() {
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                // Formula to map any Lat/Lng to a District ID (1-50) used in your Python script
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
            // High Risk - Red Theme
            llRiskStatus.setBackgroundColor(Color.parseColor("#FFCDD2"))
            tvRiskStatus.setTextColor(Color.parseColor("#B71C1C"))
            Toast.makeText(this, "⚠️ High Risk Zone Detected!", Toast.LENGTH_SHORT).show()
        } else {
            // Safe - Blue Theme
            llRiskStatus.setBackgroundColor(Color.parseColor("#E1F5FE"))
            tvRiskStatus.setTextColor(Color.parseColor("#01579B"))
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
                val mapsLink = "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}"
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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
        val prefs = getSharedPreferences("SOS_Prefs", MODE_PRIVATE)
        val selectedPhone = prefs.getString("selected_phone", "")
        if (!selectedPhone.isNullOrEmpty()) {
            etPhone.setText(selectedPhone)
            prefs.edit().remove("selected_phone").apply()
        }

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL)

        // Re-run risk analysis when user returns to app
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runRiskAnalysis()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}