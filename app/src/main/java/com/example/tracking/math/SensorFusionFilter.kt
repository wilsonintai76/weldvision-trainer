package com.example.tracking.math

import kotlin.math.sqrt

/**
 * Complementary filter that blends high-frequency IMU gyroscope data
 * with low-frequency absolute camera transformations.
 *
 * The fused quaternion orientation q_fused(t) is updated by:
 *   q_fused(t) = α · (q_fused(t-Δt) ⊗ q_gyro(Δt)) + (1-α) · q_cam(t)
 *
 * where:
 *   α = filter weight (0.92–0.98, higher = more gyro trust)
 *   ⊗ = quaternion multiplication (Hamilton product)
 *   q_gyro(Δt) = incremental rotation from angular velocity integration
 *
 * Usage:
 *   val filter = SensorFusionFilter(alpha = 0.95f)
 *   filter.processGyroscope(floatArrayOf(wx, wy, wz), timestampNanos)
 *   filter.processCameraUpdate(cameraQuaternion)
 *   val orientation = filter.getFusedOrientation()
 */
class SensorFusionFilter(private val alpha: Float = 0.95f) {

    private var fusedQuaternion: Quaternion = Quaternion.IDENTITY
    private var lastTimestamp: Long = 0L

    // ── Gyroscope Integration ──────────────────────────────────────────

    /**
     * Integrates high-frequency gyroscope data into the fused orientation.
     * Call this on every gyroscope sensor event (~100 Hz).
     *
     * @param gyroData  Raw [x, y, z] angular velocity in rad/s
     * @param timestamp System time in nanoseconds (System.nanoTime())
     */
    fun processGyroscope(gyroData: FloatArray, timestamp: Long) {
        require(gyroData.size == 3) { "gyroData must be 3-element array [wx, wy, wz]" }

        if (lastTimestamp == 0L) {
            lastTimestamp = timestamp
            return
        }

        val dt = (timestamp - lastTimestamp) * 1.0e-9f // ns → seconds
        lastTimestamp = timestamp
        if (dt <= 0f || dt > 0.5f) return // Skip invalid/stale readings

        val omegaMag = sqrt(gyroData[0] * gyroData[0] + gyroData[1] * gyroData[1] + gyroData[2] * gyroData[2])
        if (omegaMag < 1.0e-5f) return // Below noise floor — no rotation

        val gyroDelta = Quaternion.fromGyroscope(gyroData, dt)
        fusedQuaternion = fusedQuaternion.multiply(gyroDelta).normalize()
    }

    // ── Camera Correction ─────────────────────────────────────────────

    /**
     * Corrects the fused orientation using a low-frequency absolute
     * camera measurement (e.g. from AprilTag PnP pose estimation).
     *
     * Uses linear interpolation (LERP) for low CPU overhead on edge devices.
     * For production, consider SLERP for constant angular velocity blending.
     *
     * @param cameraRotation Camera orientation as a quaternion
     */
    fun processCameraUpdate(cameraRotation: Quaternion) {
        fusedQuaternion = fusedQuaternion.lerp(cameraRotation, 1f - alpha).normalize()
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** Returns the current fused orientation quaternion. */
    fun getFusedOrientation(): Quaternion = fusedQuaternion

    /** Returns Euler angles [roll, pitch, yaw] in radians. */
    fun getEulerAngles(): FloatArray = fusedQuaternion.toEuler()

    /** Resets the filter to identity orientation. */
    fun reset() {
        fusedQuaternion = Quaternion.IDENTITY
        lastTimestamp = 0L
    }

    /** Returns true if the filter has received at least one gyro reading. */
    fun isInitialized(): Boolean = lastTimestamp != 0L
}
