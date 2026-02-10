package com.example.safeguardai_2

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CrimePredictor(context: Context) {
    private var interpreter: Interpreter? = null

    init {
        val assetFileDescriptor = context.assets.openFd("crime_predictor.tflite")
        val inputStream = assetFileDescriptor.createInputStream()
        val modelBuffer = inputStream.readBytes()
        val byteBuffer = ByteBuffer.allocateDirect(modelBuffer.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(modelBuffer)
        interpreter = Interpreter(byteBuffer)
    }

    fun getRiskScore(districtId: Int, hour: Int): Float {
        val input = floatArrayOf(districtId.toFloat(), hour.toFloat())
        val output = Array(1) { FloatArray(1) }
        interpreter?.run(input, output)
        return output[0][0] // Result: 0.0 (Safe) to 1.0 (Danger)
    }
}