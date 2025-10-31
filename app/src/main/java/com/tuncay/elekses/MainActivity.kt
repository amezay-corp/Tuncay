package com.tuncay.elekses

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MainActivity : AppCompatActivity() {
    private lateinit var labelTv: TextView
    private var mqttClient: MqttClient? = null
    private var mediaPlayer: MediaPlayer? = null
    private val brokerUrl = "tcp://78.187.16.248:1883"
    private val clientId = "AndroidClient_${System.currentTimeMillis()}"
    private val topic = "elektrik/8"
    private var lastState: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        labelTv = findViewById(R.id.labelTv)
        labelTv.text = "TUNCAY MÜHENDİSLİK"

        // Request camera permission for flash if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        connectMqttSafely()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient?.disconnect()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
    }

    private fun connectMqttSafely() {
        Thread {
            try {
                val persistence = MemoryPersistence()
                mqttClient = MqttClient(brokerUrl, clientId, persistence)
                val options = MqttConnectOptions()
                options.isAutomaticReconnect = true
                options.isCleanSession = true
                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        runOnUiThread { labelTv.text = "MQTT bağlantısı koptu" }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString()?.trim() ?: return
                        runOnUiThread { handleMessage(payload) }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                mqttClient?.connect(options)
                mqttClient?.subscribe(topic, 1)
                runOnUiThread { labelTv.text = "MQTT bağlı: $brokerUrl" }
            } catch (e: Exception) {
                runOnUiThread { labelTv.text = "MQTT bağlanma hatası: ${e.message}" }
            }
        }.start()
    }

    private fun handleMessage(payload: String) {
        labelTv.text = if (payload == "ON") "Elektrik Var" else if (payload == "OFF") "Elektrik Yok" else payload
        if (payload == lastState) return
        lastState = payload
        when (payload) {
            "ON" -> {
                playSoundSafely(R.raw.elek_ok)
                blinkFlashSafely(3)
            }
            "OFF" -> {
                playSoundSafely(R.raw.elekt_yok)
                blinkFlashSafely(3)
            }
        }
    }

    private fun playSoundSafely(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        try {
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.setOnCompletionListener {
                try { it.release() } catch (_: Exception) {}
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            // ignore playback errors (missing resource, etc.)
        }
    }

    private fun blinkFlashSafely(times: Int, onMs: Long = 200, offMs: Long = 200) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cameraManager = getSystemService(CAMERA_SERVICE) as? CameraManager ?: return
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (e: Exception) {
                    false
                }
            } ?: return

            for (i in 0 until times) {
                handler.postDelayed({
                    try { cameraManager.setTorchMode(cameraId, true) } catch (e: CameraAccessException) {}
                }, (i * 2L) * (onMs + offMs))
                handler.postDelayed({
                    try { cameraManager.setTorchMode(cameraId, false) } catch (e: CameraAccessException) {}
                }, (i * 2L + 1) * (onMs + offMs))
            }
        } catch (e: Exception) {
            // ignore flash errors
        }
    }
}
