package com.example.tracking

import com.example.tracking.math.PivotCalibrator
import com.example.tracking.math.Quaternion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.acos

enum class CalibrationState {
    IDLE,             // Waiting to start
    INSTRUCT_PIVOT,   // Tell user to place torch tip on a fixed pivot point
    COLLECTING,       // User is swiveling the torch, collecting samples
    SOLVING,          // 10+ samples collected, running Least Squares math
    SUCCESS,          // Successfully calculated Tip Offset
    FAILED            // Calculation failed (likely didn't swivel enough, singular matrix)
}

/**
 * Manages the "Pivot Sweep" procedure to dynamically calculate the 3D torch tip offset.
 * Operates independently of the daily student tracking workflow.
 */
class CalibrationSessionManager {

    private val _state = MutableStateFlow(CalibrationState.IDLE)
    val state: StateFlow<CalibrationState> = _state.asStateFlow()

    private val calibrator = PivotCalibrator()
    private var lastSampledRotation: Quaternion? = null
    
    /** The calculated 3D offset [tx, ty, tz] in mm. Valid only in SUCCESS state. */
    var calculatedTipOffsetMm: FloatArray? = null
        private set

    // Configuration
    private val angularDistanceThresholdRad = 0.087f // ~5 degrees
    private val targetSampleCount = 15

    fun startCalibration() {
        _state.value = CalibrationState.INSTRUCT_PIVOT
        calibrator.reset()
        lastSampledRotation = null
        calculatedTipOffsetMm = null
    }

    fun beginCollecting() {
        if (_state.value == CalibrationState.INSTRUCT_PIVOT) {
            _state.value = CalibrationState.COLLECTING
        }
    }
    
    fun reset() {
        _state.value = CalibrationState.IDLE
        calibrator.reset()
        lastSampledRotation = null
        calculatedTipOffsetMm = null
    }

    /**
     * Call this every frame during the calibration process with the latest 
     * Camera (or Fused) orientation and translation.
     * 
     * @param cameraTranslationMm The camera's translation in world frame.
     * @param cameraRotation The camera's orientation in world frame.
     */
    fun update(cameraTranslationMm: FloatArray, cameraRotation: Quaternion) {
        if (_state.value != CalibrationState.COLLECTING) return

        val addSample = if (lastSampledRotation == null) {
            true
        } else {
            // Calculate angular distance between current rotation and last sampled rotation
            // Theta = 2 * acos(|q1 dot q2|)
            val lastQ = lastSampledRotation!!
            val dot = (cameraRotation.w * lastQ.w + 
                       cameraRotation.x * lastQ.x + 
                       cameraRotation.y * lastQ.y + 
                       cameraRotation.z * lastQ.z).coerceIn(-1f, 1f)
            val angularDistance = 2f * acos(abs(dot))
            
            angularDistance >= angularDistanceThresholdRad
        }

        if (addSample) {
            calibrator.addSample(cameraRotation.toRotationMatrix(), cameraTranslationMm)
            lastSampledRotation = cameraRotation
            
            // Once we hit our target, automatically solve
            if (calibrator.getSampleSize() >= targetSampleCount) {
                _state.value = CalibrationState.SOLVING
                solve()
            }
        }
    }

    private fun solve() {
        val offset = calibrator.computeCalibrationOffset()
        if (offset != null) {
            calculatedTipOffsetMm = offset
            _state.value = CalibrationState.SUCCESS
        } else {
            // The math failed, likely because the poses were coplanar (didn't swivel on multiple axes)
            _state.value = CalibrationState.FAILED
        }
    }
    
    fun getCollectedSampleCount(): Int = calibrator.getSampleSize()
    fun getTargetSampleCount(): Int = targetSampleCount
}
