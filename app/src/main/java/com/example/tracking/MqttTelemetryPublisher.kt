package com.example.tracking

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Dual Lane MQTT Telemetry Publisher
 * Attempts to connect to a local Mosquitto broker first. If that fails,
 * it falls back to a cloud HiveMQ broker.
 */
class MqttTelemetryPublisher {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var client: Mqtt3AsyncClient? = null
    private var isConnected = false

    private val localHost = "weld-broker.local"
    private val localPort = 1883
    
    private val cloudHost = "e6c9c71794bd4f61895d032657d876a2.s1.eu.hivemq.cloud"
    private val cloudPort = 8883
    
    // TODO: Ideally populate these from BuildConfig / .env
    private val cloudUser = "weld_student_1" 
    private val cloudPass = "placeholder_password"

    fun connect() {
        if (isConnected) return
        
        Log.i("MqttTelemetryPublisher", "Attempting connection to Primary Local Lane: $localHost:$localPort")
        
        // Attempt Primary Local Mosquitto
        val localClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier("WeldVisionApp-${UUID.randomUUID().toString().take(8)}")
            .serverHost(localHost)
            .serverPort(localPort)
            .buildAsync()

        localClient.connect()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    Log.i("MqttTelemetryPublisher", "Connected to Local Mosquitto LAN")
                    client = localClient
                    isConnected = true
                } else {
                    Log.w("MqttTelemetryPublisher", "Local connection failed. Falling back to WAN (HiveMQ Cloud)")
                    connectFallback()
                }
            }
    }

    private fun connectFallback() {
        val fallbackClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier("WeldVisionApp-${UUID.randomUUID().toString().take(8)}")
            .serverHost(cloudHost)
            .serverPort(cloudPort)
            .sslWithDefaultConfig()
            .simpleAuth()
                .username(cloudUser)
                .password(cloudPass.toByteArray())
                .applySimpleAuth()
            .buildAsync()

        fallbackClient.connect()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    Log.i("MqttTelemetryPublisher", "Connected to HiveMQ Cloud WAN Fallback")
                    client = fallbackClient
                    isConnected = true
                } else {
                    Log.e("MqttTelemetryPublisher", "WAN Fallback connection failed too: ${throwable?.message}")
                }
            }
    }

    /**
     * Pushes a lightweight JSON payload of the current tracker state.
     * Slimmed down for 60Hz processing.
     */
    fun publishTelemetry(state: HybridWeldTracker.TrackerState) {
        if (!isConnected || client == null) return
        if (!state.tagVisible) return // Don't stream if we aren't tracking anything

        // Offload to IO thread so we don't block CameraX / Main thread
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("tx", String.format("%.2f", state.tipTranslationMm[0]))
                    put("ty", String.format("%.2f", state.tipTranslationMm[1]))
                    put("tz", String.format("%.2f", state.tipTranslationMm[2]))
                    put("workAngle", String.format("%.1f", state.workAngle))
                    put("travelAngle", String.format("%.1f", state.travelAngle))
                    put("speed", String.format("%.2f", state.tipSpeedMmPerSec))
                    put("progress", String.format("%.3f", state.travelProgress))
                }.toString()

                client?.publishWith()
                    ?.topic("weldvision/telemetry/live")
                    ?.payload(payload.toByteArray())
                    ?.send()
            } catch (e: Exception) {
                // Ignore serialization or publish errors to maintain 60Hz stability
            }
        }
    }
    
    fun disconnect() {
        client?.disconnect()
        isConnected = false
    }
}
