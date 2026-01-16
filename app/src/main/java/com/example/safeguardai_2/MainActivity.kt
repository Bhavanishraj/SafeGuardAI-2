package com.example.safeguardai_2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var cbSendAll: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etPhone = findViewById(R.id.etPhone)
        cbSendAll = findViewById(R.id.cbSendAll)

        findViewById<Button>(R.id.btnViewContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            checkPermissionsAndSend()
        }
    }

    private fun checkPermissionsAndSend() {
        val permissions = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.all { it.value }) sendSOS()
        }.launch(permissions)
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
            recipients.add(etPhone.text.toString())
        }

        if (recipients.isEmpty()) return

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val msg = "SOS! I need help. Maps: http://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                val smsManager = getSystemService(SmsManager::class.java)
                recipients.forEach { num ->
                    val parts = smsManager.divideMessage(msg)
                    smsManager.sendMultipartTextMessage(num, null, parts, null, null)
                }
                Toast.makeText(this, "SOS Sent!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}