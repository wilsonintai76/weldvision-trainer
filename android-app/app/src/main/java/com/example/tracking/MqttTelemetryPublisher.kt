package com.example.tracking

import android.util.Log

/**
 * Local-Only MQTT Telemetry Publisher
 * ====================================
 *
 * Thin backward-compatible wrapper that delegates to MultiUserPublisher.
 * Connects exclusively to the instructor's local Mosquitto broker — no cloud
 * fallback. HiveMQ has been dropped because the free tier cannot handle
 * multi-user 60 Hz telemetry volume.
 *
 * For new code, use MultiUserPublisher directly to get per-student dynamic
 * topic routing and student metadata in payloads.
 *
 * Architecture:
 *   Phone ──► Mosquitto Broker (instructor laptop) ◄── PWA Dashboard
 *   Topic:  weldvision/student/{seat}/live
 *   LAN:    TCP 1883 (Android) + WebSocket 9001 (Browser)
 */
class MqttTelemetryPublisher(
    private val brokerIp: String = "weld-broker.local",
    private val studentName: String = "Student",
    private val seatIdentifier: String = "seat_01"
) {
    companion object {
        private const val TAG = "MqttTelemetryPublisher"
    }

    private val delegate = MultiUserPublisher(
        brokerIp = brokerIp,
        studentName = studentName,
        seatIdentifier = seatIdentifier
    )

    fun connect() {
        Log.i(TAG, "Connecting to local Mosquitto broker at $brokerIp:1883")
        delegate.connect()
    }

    fun publishTelemetry(state: HybridWeldTracker.TrackerState) {
        delegate.publishTelemetry(state)
    }

    fun disconnect() {
        delegate.disconnect()
    }
}
