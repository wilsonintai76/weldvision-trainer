package com.example.tracking

import android.util.Log
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Multi-User Classroom MQTT Telemetry Publisher
 * ==============================================
 *
 * Publishes per-student telemetry frames to the instructor's local Mosquitto
 * broker using dynamically-routed MQTT topics:
 *
 *   weldvision/student/{seatIdentifier}/live
 *
 * The instructor dashboard subscribes to the single wildcard topic:
 *
 *   weldvision/student/+/live
 *
 * This isolates each student's data stream and prevents frame collisions
 * when multiple students weld simultaneously in the same classroom.
 *
 * Architecture:
 *
 *   [Student 1 Phone] ──► Topic: weldvision/student/seat_01/live ──┐
 *   [Student 2 Phone] ──► Topic: weldvision/student/seat_02/live ──┼─► [Instructor Laptop]
 *   [Student 3 Phone] ──► Topic: weldvision/student/seat_03/live ──┘    Mosquitto Broker
 *
 * @param brokerIp      IP address of the instructor's laptop (discovered via NSD/mDNS)
 * @param studentName   Display name entered by student at login (e.g. "Alex Miller")
 * @param seatIdentifier Unique seat/station ID (e.g. "seat_04")
 * @param studentId     Optional student ID for backend sync (e.g. "STU-4201")
 */
class MultiUserPublisher(
    private val brokerIp: String,
    private val studentName: String,
    private val seatIdentifier: String,
    private val studentId: String = ""
) {
    companion object {
        private const val TAG = "MultiUserPublisher"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var client: Mqtt3AsyncClient? = null
    private var isConnected = false

    /** Dynamically structured topic unique to this student's workspace. */
    val dynamicTopic = "weldvision/student/$seatIdentifier/live"

    // ── Connection ──────────────────────────────────────────────────

    /**
     * Connects to the classroom Mosquitto broker running on the instructor's laptop.
     * Uses a strict client ID (seat-based) to prevent session collisions when
     * multiple students connect from the same IP range.
     */
    fun connect() {
        if (isConnected) return

        val clientId = "weldvision_${seatIdentifier}_${System.currentTimeMillis() % 10000}"

        Log.i(TAG, "Connecting to classroom broker at $brokerIp:1883 as $clientId")

        client = Mqtt3Client.builder()
            .identifier(clientId)
            .serverHost(brokerIp)
            .serverPort(1883)
            .automaticReconnect()
                .initialDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .maxDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                .applyAutomaticReconnect()
            .buildAsync()

        client?.connect()
            ?.whenComplete { _, throwable ->
                if (throwable == null) {
                    Log.i(TAG, "Connected to classroom broker. Topic: $dynamicTopic")
                    isConnected = true
                } else {
                    Log.e(TAG, "Broker connection failed: ${throwable.message}")
                    isConnected = false
                }
            }
    }

    // ── Telemetry Publishing ────────────────────────────────────────

    /**
     * Packages spatial coordinates alongside student profile metadata
     * into a JSON payload and publishes to the student's isolated topic.
     *
     * QoS 0 (AT_MOST_ONCE) for maximum throughput at 60 Hz.
     * Frames arrive as fire-and-forget — the dashboard handles
     * ordering and missing-frame interpolation.
     */
    fun publishTelemetry(state: HybridWeldTracker.TrackerState) {
        if (!isConnected || client == null) return
        if (!state.tagVisible) return

        scope.launch {
            try {
                val now = System.currentTimeMillis()

                // Build payload with embedded metadata so the dashboard
                // can map each frame to the correct student roster card
                val payload = buildString {
                    append("{")
                    append("\"meta\":{")
                    append("\"name\":\"${escapeJson(studentName)}\",")
                    append("\"seat\":\"${escapeJson(seatIdentifier)}\",")
                    append("\"studentId\":\"${escapeJson(studentId)}\"")
                    append("},")
                    append("\"spatial\":{")
                    append("\"x\":${fmt(state.tipTranslationMm[0])},")
                    append("\"y\":${fmt(state.tipTranslationMm[1])},")
                    append("\"z\":${fmt(state.tipTranslationMm[2])}")
                    append("},")
                    append("\"angles\":{")
                    append("\"work\":${fmt(state.workAngle)},")
                    append("\"travel\":${fmt(state.travelAngle)}")
                    append("},")
                    append("\"travel\":{")
                    append("\"speed\":${fmt(state.tipSpeedMmPerSec)},")
                    append("\"progress\":${fmt(state.travelProgress)},")
                    append("\"distanceMm\":${fmt(state.travelDistanceMm)}")
                    append("},")
                    append("\"timestamp\":$now")
                    append("}")
                }

                client?.publishWith()
                    ?.topic(dynamicTopic)
                    ?.payload(payload.toByteArray())
                    ?.qos(MqttQos.AT_MOST_ONCE)
                    ?.send()
            } catch (e: Exception) {
                // Silently drop to maintain 60 Hz stability
            }
        }
    }

    // ── Status Reporting ────────────────────────────────────────────

    /**
     * Publishes a lightweight heartbeat/status frame on a separate topic
     * so the instructor dashboard can track connection health.
     */
    fun publishStatus(isWelding: Boolean, score: Int = 0, defectLabel: String = "") {
        if (!isConnected || client == null) return

        scope.launch {
            try {
                val payload = buildString {
                    append("{")
                    append("\"meta\":{")
                    append("\"name\":\"${escapeJson(studentName)}\",")
                    append("\"seat\":\"${escapeJson(seatIdentifier)}\"")
                    append("},")
                    append("\"status\":{")
                    append("\"isWelding\":$isWelding,")
                    append("\"score\":$score,")
                    append("\"defect\":\"${escapeJson(defectLabel)}\"")
                    append("},")
                    append("\"timestamp\":${System.currentTimeMillis()}")
                    append("}")
                }

                client?.publishWith()
                    ?.topic("weldvision/student/$seatIdentifier/status")
                    ?.payload(payload.toByteArray())
                    ?.qos(MqttQos.AT_MOST_ONCE)
                    ?.send()
            } catch (_: Exception) { }
        }
    }

    // ── Teardown ────────────────────────────────────────────────────

    fun disconnect() {
        Log.i(TAG, "Disconnecting seat $seatIdentifier from broker")
        client?.disconnect()
        isConnected = false
        client = null
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun fmt(v: Float): String = "%.2f".format(v)
    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
