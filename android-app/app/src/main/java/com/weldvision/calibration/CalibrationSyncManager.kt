package com.weldvision.calibration

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages the WebSocket connection to the WeldVision Telemetry Server.
 *
 * Responsibilities:
 * - Register this device as a student session
 * - Push calibration profiles for instructor review
 * - Stream real-time telemetry frames during welding
 * - Receive recalibration requests from the instructor
 */
class CalibrationSyncManager(
    private val serverUrl: String,
    private val bracketId: String,
    private val studentId: String
) {
    companion object {
        private const val TAG = "CalibSync"
        private const val RECONNECT_DELAY_SEC = 5L
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    // ── Callbacks ────────────────────────────────────

    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((code: Int, reason: String) -> Unit)? = null
    var onRecalibrationRequested: ((reason: String) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null

    // ── Connect ──────────────────────────────────────

    fun connect() {
        shouldReconnect = true
        reconnectAttempts = 0
        doConnect()
    }

    private fun doConnect() {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $serverUrl")
                reconnectAttempts = 0

                // Register as student
                val registerMsg = JSONObject().apply {
                    put("type", "register")
                    put("bracketId", bracketId)
                    put("studentId", studentId)
                    put("role", "student")
                }
                ws.send(registerMsg.toString())
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Server closing connection: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Disconnected: $code $reason")
                onDisconnected?.invoke(code, reason)
                attemptReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}", t)
                onError?.invoke(t.message ?: "Unknown error")
                attemptReconnect()
            }
        })
    }

    // ── Reconnect logic ──────────────────────────────

    private fun attemptReconnect() {
        if (!shouldReconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached. Giving up.")
            return
        }

        reconnectAttempts++
        val delay = RECONNECT_DELAY_SEC * reconnectAttempts
        Log.i(TAG, "Reconnecting in ${delay}s (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        Thread {
            Thread.sleep(delay * 1000)
            if (shouldReconnect) {
                doConnect()
            }
        }.start()
    }

    // ── Handle incoming messages ─────────────────────

    private fun handleIncomingMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val type = msg.optString("type", "")

            when (type) {
                "recalibration_request" -> {
                    val reason = msg.optString("reason", "Calibration quality insufficient")
                    Log.i(TAG, "Recalibration requested: $reason")
                    onRecalibrationRequested?.invoke(reason)
                }
                "error" -> {
                    val message = msg.optString("message", "Unknown error")
                    Log.w(TAG, "Server error: $message")
                    onError?.invoke(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming message", e)
        }
    }

    // ── Push calibration profile ─────────────────────

    fun pushCalibrationProfile(profile: CalibrationProfile) {
        val message = JSONObject().apply {
            put("type", "calibration_profile")
            put("bracketId", bracketId)
            put("studentId", studentId)
            put("deviceModel", profile.deviceModel)
            put("profile", JSONObject().apply {
                // TCP
                profile.tcp?.let { tcp ->
                    put("tcp", JSONObject().apply {
                        put("offsetX", tcp.offsetX)
                        put("offsetY", tcp.offsetY)
                        put("offsetZ", tcp.offsetZ)
                        put("quality", JSONObject().apply {
                            put("coveragePercent", tcp.quality.coveragePercent)
                            put("conditionNumber", tcp.quality.conditionNumber)
                            put("residualMm", tcp.quality.residualMm)
                            put("reprojectionMeanMm", tcp.quality.reprojectionMeanMm)
                            put("reprojectionMaxMm", tcp.quality.reprojectionMaxMm)
                            put("sampleCount", tcp.quality.sampleCount)
                        })
                    })
                }
                // Workpiece
                profile.workpiece?.let { wp ->
                    put("workpiece", JSONObject().apply {
                        put("method", wp.method)
                        put("seamLengthMm", wp.seamLengthMm)
                        put("valid", wp.valid)
                    })
                }
            })
        }

        send(message.toString())
    }

    // ── Push telemetry frame ─────────────────────────

    fun pushTelemetryFrame(
        sessionId: String,
        tipPosX: Double,
        tipPosY: Double,
        tipPosZ: Double,
        trackingConfidence: Double,
        travelAngleDeg: Double? = null,
        workAngleDeg: Double? = null
    ) {
        val message = JSONObject().apply {
            put("type", "telemetry_frame")
            put("sessionId", sessionId)
            put("bracketId", bracketId)
            put("studentId", studentId)
            put("timestampMs", System.currentTimeMillis())
            put("tipPosition", JSONObject().apply {
                put("x_mm", tipPosX)
                put("y_mm", tipPosY)
                put("z_mm", tipPosZ)
            })
            if (travelAngleDeg != null || workAngleDeg != null) {
                put("torchAngles", JSONObject().apply {
                    travelAngleDeg?.let { put("travelAngle_deg", it) }
                    workAngleDeg?.let { put("workAngle_deg", it) }
                })
            }
            put("quality", JSONObject().apply {
                put("trackingConfidence", trackingConfidence)
                put("tagVisible", trackingConfidence > 0.5)
            })
        }

        send(message.toString())
    }

    // ── Session lifecycle ────────────────────────────

    fun pushSessionStart(sessionId: String) {
        val message = JSONObject().apply {
            put("type", "session_start")
            put("sessionId", sessionId)
            put("bracketId", bracketId)
            put("studentId", studentId)
            put("timestampMs", System.currentTimeMillis())
        }
        send(message.toString())
    }

    fun pushSessionEnd(sessionId: String, totalTravelMm: Double, durationSec: Double) {
        val message = JSONObject().apply {
            put("type", "session_end")
            put("sessionId", sessionId)
            put("timestampMs", System.currentTimeMillis())
            put("totalTravelMm", totalTravelMm)
            put("durationSec", durationSec)
        }
        send(message.toString())
    }

    // ── Send helper ──────────────────────────────────

    private fun send(payload: String) {
        val ws = webSocket
        if (ws != null) {
            val sent = ws.send(payload)
            if (!sent) {
                Log.w(TAG, "Failed to send message — queued by OkHttp")
            }
        } else {
            Log.w(TAG, "WebSocket not connected — message dropped")
        }
    }

    // ── Disconnect ───────────────────────────────────

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Session complete")
        webSocket = null
    }
}
