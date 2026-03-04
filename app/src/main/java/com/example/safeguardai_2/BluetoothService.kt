package com.example.safeguardai_2

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.InputStream
import java.util.*

class BluetoothService : Service() {
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var isRunning = false

    // Must match your ESP32 Bluetooth name exactly
    private val deviceName = "Safeguard_AI"
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            Thread { connectToDevice() }.start()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        while (isRunning) {
            val device = bluetoothAdapter?.bondedDevices?.find { it.name == deviceName }
            if (device != null) {
                try {
                    Log.d("BT_SERVICE", "Attempting connection to $deviceName...")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket?.connect()
                    inputStream = socket?.inputStream
                    Log.d("BT_SERVICE", "Successfully connected!")
                    listenForData()
                } catch (e: Exception) {
                    Log.e("BT_SERVICE", "Connection failed: ${e.message}. Retrying in 5s...")
                    Thread.sleep(5000)
                }
            } else {
                Log.e("BT_SERVICE", "Device $deviceName not found in paired list. Retrying in 10s...")
                Thread.sleep(10000)
            }
        }
    }

    private fun listenForData() {
        val buffer = ByteArray(1024)
        var bytes: Int

        while (socket?.isConnected == true && isRunning) {
            try {
                bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val rawMessage = String(buffer, 0, bytes).trim()
                    // Logging raw data helps debug if the ESP32 is sending the wrong format
                    Log.d("BT_SERVICE", "Raw Data: $rawMessage")
                    handleIncomingData(rawMessage)
                }
            } catch (e: Exception) {
                Log.e("BT_SERVICE", "Stream broken: ${e.message}")
                break
            }
        }
    }

    private fun handleIncomingData(data: String) {
        // Logic to handle "DATA:BPM:85,SOUND:300"
        if (data.contains("DATA:")) {
            val cleanData = data.substringAfter("DATA:").trim()
            val intent = Intent("ESP32_DATA")
            intent.putExtra("raw", cleanData)
            sendBroadcast(intent)
        }
        // Logic to handle "ALERT:Panic"
        else if (data.contains("ALERT:")) {
            val intent = Intent("ESP32_EMERGENCY")
            sendBroadcast(intent)
        }
    }

    override fun onDestroy() {
        isRunning = false
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e("BT_SERVICE", "Close error")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}