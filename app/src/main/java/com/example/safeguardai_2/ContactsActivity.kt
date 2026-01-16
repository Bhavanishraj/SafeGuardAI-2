package com.example.safeguardai_2

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContactsActivity : AppCompatActivity() {

    private var contactsList = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts) // Create this XML or reuse activity_main (list version)

        loadContacts()

        val rv = findViewById<RecyclerView>(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ContactAdapter(contactsList)
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAddContact).setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    contactsList.add(Contact(name, phone))
                    saveContacts()
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveContacts() {
        val prefs = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(contactsList)
        prefs.edit().putString("contact_list", json).apply()
    }

    private fun loadContacts() {
        val prefs = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("contact_list", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<Contact>>() {}.type
            contactsList = Gson().fromJson(json, type)
        }
    }

    inner class ContactAdapter(private val list: List<Contact>) : RecyclerView.Adapter<ContactAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvContactName)
            val phone: TextView = v.findViewById(R.id.tvContactPhone)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.name.text = list[position].name
            holder.phone.text = list[position].phone
        }
        override fun getItemCount() = list.size
    }
}