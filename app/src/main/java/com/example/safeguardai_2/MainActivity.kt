package com.example.safeguardai_2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var etPhone: EditText
    private lateinit var cbSendAll: CheckBox
    private lateinit var switchShake: SwitchCompat

    // Shake detection variables
    private lateinit var sensorManager: SensorManager
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeCount = 0
    private var lastShakeTime: Long = 0

    // Permission launcher MUST be defined at the class level to avoid crashes
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

        // Initialize UI components
        etPhone = findViewById(R.id.etPhone)
        cbSendAll = findViewById(R.id.cbSendAll)
        switchShake = findViewById(R.id.switchShake)

        // Initialize Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        findViewById<Button>(R.id.btnViewContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            checkPermissionsAndTrigger()
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
            Toast.makeText(this, "No recipients found", Toast.LENGTH_SHORT).show()
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                // FIXED: Use ${} for variables in strings to prevent failures
                val mapsLink = "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}"
                val msg = "🚨 SOS ALERT! I need help. My location: $mapsLink"
                val smsManager = getSystemService(SmsManager::class.java)

                recipients.forEach { num ->
                    val parts = smsManager.divideMessage(msg)
                    smsManager.sendMultipartTextMessage(num, null, parts, null, null)
                }
                Toast.makeText(this, "SOS Sent!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to get location. Is GPS on?", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Shake Detected!", Toast.LENGTH_SHORT).show()
                    checkPermissionsAndTrigger()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("SOS_Prefs", MODE_PRIVATE)
        val selectedPhone = prefs.getString("selected_phone", "")
        if (!selectedPhone.isNullOrEmpty()) {
            etPhone.setText(selectedPhone)
            prefs.edit().remove("selected_phone").apply() // Clear after use
        }

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}