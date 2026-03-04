package com.example.safeguardai_2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ContactsActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var lvTrusted: ListView
    private val trustedDisplayList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // Bind Views - Ensure these IDs match your XML exactly
        etName = findViewById(R.id.etContactName)
        etPhone = findViewById(R.id.etContactPhone)
        lvTrusted = findViewById(R.id.lvTrustedCircle)
        val btnAdd = findViewById<Button>(R.id.btnAddManual)
        val btnClear = findViewById<Button>(R.id.btnClearCircle)

        // Custom Adapter with Context Safety
        adapter = object : ArrayAdapter<String>(this@ContactsActivity, android.R.layout.simple_list_item_1, trustedDisplayList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(android.R.id.text1)
                text.setTextColor(Color.BLACK)
                text.textSize = 18f
                return view
            }
        }
        lvTrusted.adapter = adapter

        loadTrustedCircle()

        // 1. CLICK TO SELECT & RETURN
        lvTrusted.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val displayData = trustedDisplayList[position]
            if (displayData != "No contacts added yet.") {
                val clean = displayData.replace("👤 ", "")
                val parts = clean.split(" - ")
                if (parts.size >= 2) {
                    val resultIntent = Intent()
                    resultIntent.putExtra("selected_name", parts[0])
                    resultIntent.putExtra("selected_phone", parts[1])
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish() // Returns to MainActivity
                }
            }
        }

        // 2. LONG PRESS TO DELETE
        lvTrusted.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val displayData = trustedDisplayList[position]
            if (displayData != "No contacts added yet.") {
                val storageKey = displayData.replace("👤 ", "").replace(" - ", "|")
                val prefs = getSharedPreferences("TrustedCircle", Context.MODE_PRIVATE)
                val currentSet = prefs.getStringSet("contact_list", emptySet())?.toMutableSet()

                if (currentSet?.remove(storageKey) == true) {
                    prefs.edit().putStringSet("contact_list", currentSet).apply()
                    loadTrustedCircle()
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                saveContact("$name|$phone")
                loadTrustedCircle()
                etName.text.clear()
                etPhone.text.clear()
            } else {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            getSharedPreferences("TrustedCircle", Context.MODE_PRIVATE).edit().clear().apply()
            loadTrustedCircle()
        }
    }

    private fun saveContact(contactData: String) {
        val prefs = getSharedPreferences("TrustedCircle", Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet("contact_list", HashSet<String>())?.toMutableSet() ?: HashSet()
        currentSet.add(contactData)
        prefs.edit().putStringSet("contact_list", currentSet).apply()
    }

    private fun loadTrustedCircle() {
        val prefs = getSharedPreferences("TrustedCircle", Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet("contact_list", emptySet())
        trustedDisplayList.clear()
        if (!currentSet.isNullOrEmpty()) {
            currentSet.forEach { trustedDisplayList.add("👤 ${it.replace("|", " - ")}") }
        } else {
            trustedDisplayList.add("No contacts added yet.")
        }
        adapter.notifyDataSetChanged()
    }
}