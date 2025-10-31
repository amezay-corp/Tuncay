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
import org.eclipse.paho.client.mqttv3.*
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
    ) { isGranted ->
        // nothing required here, flash blinking will check permission again
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        labelTv = findViewById(R.id.labelTv)
        labelTv.text = "TUNCAY MÜHENDİSLİK"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        connectMqtt()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient?.disconnect()
        } catch (_: Exception) {}
        mediaPlayer?.release()
    }

    private fun connectMqtt() {
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
    }

    private fun handleMessage(payload: String) {
        labelTv.text = if (payload == "ON") "Elektrik Var" else if (payload == "OFF") "Elektrik Yok" else payload
        if (payload == lastState) return
        lastState = payload
        when (payload) {
            "ON" -> {
                playSound(R.raw.elek_ok)
                blinkFlash(3)
            }
            "OFF" -> {
                playSound(R.raw.elekt_yok)
                blinkFlash(3)
            }
        }
    }

    private fun playSound(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = MediaPlayer.create(this, resId)
        mediaPlayer?.setOnCompletionListener {
            it.release()
        }
        mediaPlayer?.start()
    }

    private fun blinkFlash(times: Int, onMs: Long = 200, offMs: Long = 200) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
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