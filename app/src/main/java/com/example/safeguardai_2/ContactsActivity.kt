package com.example.safeguardai_2

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper // Required import
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContactsActivity : AppCompatActivity() {

    private var contactsList = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        loadContacts()

        val rv = findViewById<RecyclerView>(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ContactAdapter(contactsList)
        rv.adapter = adapter

        // --- SWIPE TO DELETE LOGIC START ---
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false // We don't need drag-and-drop

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedContact = contactsList[position]

                // Remove from local list
                contactsList.removeAt(position)
                // Remove from permanent storage
                saveContacts()
                // Animate removal in UI
                adapter.notifyItemRemoved(position)

                Toast.makeText(this@ContactsActivity, "${deletedContact.name} deleted", Toast.LENGTH_SHORT).show()
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(rv)
        // --- SWIPE TO DELETE LOGIC END ---

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

    inner class ContactAdapter(private val list: MutableList<Contact>) : RecyclerView.Adapter<ContactAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvContactName)
            val phone: TextView = v.findViewById(R.id.tvContactPhone)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val contact = list[position]
            holder.name.text = contact.name
            holder.phone.text = contact.phone

            holder.itemView.setOnClickListener {
                val prefs = holder.itemView.context.getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("selected_phone", contact.phone).apply()
                Toast.makeText(holder.itemView.context, "Selected: ${contact.name}", Toast.LENGTH_SHORT).show()
                (holder.itemView.context as ContactsActivity).finish()
            }
        }
        override fun getItemCount() = list.size
    }
}