package com.example.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class SessionState {
    IDLE,             // Initial state
    SCANNING,         // Camera on, looking for tags
    TIP_TRACKING,     // Tag found, showing tip in real-time
    AWAITING_START,   // Tip is stationary, 1-second countdown
    ARMED,            // Countdown complete, ready to weld
    WELDING,          // Moving, tracking distance
    SESSION_COMPLETE  // Lifted off
}

/**
 * Manages the zero-touch calibration and session workflow for a welding student.
 * Drives transitions based on raw tracking data from [HybridWeldTracker].
 */
class WeldSessionManager(private val tracker: HybridWeldTracker) {

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    var startPointMm: FloatArray? = null
        private set

    private var stationaryStartTime: Long = 0L
    private val stationaryThresholdMmPerSec = 2.0f
    private val stationaryDurationMs = 1000L
    private val liftoffThresholdMm = 50.0f

    /**
     * Called when the student opens the app and is ready to start scanning for the fixture.
     */
    fun startScanning() {
        if (_sessionState.value == SessionState.IDLE || _sessionState.value == SessionState.SESSION_COMPLETE) {
            _sessionState.value = SessionState.SCANNING
            tracker.reset()
            startPointMm = null
        }
    }

    /**
     * Reset the workflow back to idle.
     */
    fun reset() {
        _sessionState.value = SessionState.IDLE
        tracker.reset()
        startPointMm = null
    }

    /**
     * Call this every frame after updating the [HybridWeldTracker].
     */
    fun update(state: HybridWeldTracker.TrackerState) {
        val now = System.currentTimeMillis()
        val currentState = _sessionState.value

        when (currentState) {
            SessionState.IDLE -> {
                // Waiting for UI to trigger startScanning()
            }
            SessionState.SCANNING -> {
                if (state.tagVisible) {
                    _sessionState.value = SessionState.TIP_TRACKING
                }
            }
            SessionState.TIP_TRACKING -> {
                if (!state.tagVisible) {
                    _sessionState.value = SessionState.SCANNING
                    return
                }
                
                // If tip is held still, start the countdown
                if (state.tipSpeedMmPerSec < stationaryThresholdMmPerSec) {
                    _sessionState.value = SessionState.AWAITING_START
                    stationaryStartTime = now
                }
            }
            SessionState.AWAITING_START -> {
                if (!state.tagVisible) {
                    _sessionState.value = SessionState.SCANNING
                    return
                }
                
                if (state.tipSpeedMmPerSec > stationaryThresholdMmPerSec) {
                    // Moved too much, abort countdown
                    _sessionState.value = SessionState.TIP_TRACKING
                } else if (now - stationaryStartTime >= stationaryDurationMs) {
                    // Held still for 1 second!
                    _sessionState.value = SessionState.ARMED
                    startPointMm = state.tipTranslationMm.clone()
                }
            }
            SessionState.ARMED -> {
                if (!state.tagVisible) {
                    _sessionState.value = SessionState.SCANNING
                    startPointMm = null
                    return
                }
                
                // Start welding as soon as they move away from the start point
                if (state.tipSpeedMmPerSec > stationaryThresholdMmPerSec) {
                    _sessionState.value = SessionState.WELDING
                }
            }
            SessionState.WELDING -> {
                if (!state.tagVisible) {
                    // For a robust system, we might have a LOST_TRACKING state.
                    // For now, assume if tag is lost, we return to scanning.
                    _sessionState.value = SessionState.SCANNING
                    startPointMm = null
                    return
                }
                
                val start = startPointMm
                if (start != null) {
                    val current = state.tipTranslationMm
                    
                    // Check for lift-off (Z-axis distance from start plane)
                    val zDist = abs(current[2] - start[2])
                    
                    if (zDist > liftoffThresholdMm) {
                        _sessionState.value = SessionState.SESSION_COMPLETE
                    }
                }
            }
            SessionState.SESSION_COMPLETE -> {
                // Session is over. UI should display summary.
                // Waiting for reset() or startScanning()
            }
        }
    }
}
