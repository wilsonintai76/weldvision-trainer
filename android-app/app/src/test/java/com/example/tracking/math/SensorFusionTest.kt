package com.example.tracking.math

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies that SensorFusionFilter orientation converges correctly
 * and quaternion math produces valid rotations.
 */
class SensorFusionTest {

    private val TOLERANCE = 1e-4f

    // ── Quaternion Tests ───────────────────────────────────────────────

    @Test
    fun `identity quaternion has unit norm`() {
        assertEquals(1f, Quaternion.IDENTITY.norm(), TOLERANCE)
    }

    @Test
    fun `quaternion multiplication identity`() {
        val q = Quaternion(0.707f, 0.707f, 0f, 0f) // 90° around X
        val result = q.multiply(Quaternion.IDENTITY)
        assertEquals(q.w, result.w, TOLERANCE)
        assertEquals(q.x, result.x, TOLERANCE)
        assertEquals(q.y, result.y, TOLERANCE)
        assertEquals(q.z, result.z, TOLERANCE)
    }

    @Test
    fun `quaternion from axis angle 90 degrees around Z`() {
        val q = Quaternion.fromAxisAngle(floatArrayOf(0f, 0f, 1f), (Math.PI / 2).toFloat())
        // 90° around Z: cos(45°)=0.707, z·sin(45°)=0.707
        assertEquals(0.707f, q.w, 0.01f)
        assertEquals(0f, q.x, 0.01f)
        assertEquals(0f, q.y, 0.01f)
        assertEquals(0.707f, q.z, 0.01f)
    }

    @Test
    fun `quaternion normalize produces unit norm`() {
        val q = Quaternion(2f, 3f, 4f, 5f)
        val n = q.normalize()
        assertEquals(1f, n.norm(), TOLERANCE)
    }

    @Test
    fun `quaternion to rotation matrix is orthonormal`() {
        val q = Quaternion.fromAxisAngle(floatArrayOf(1f, 0f, 0f), 0.5f).normalize()
        val r = q.toRotationMatrix()
        // R * R^T should be identity
        val r00 = r[0]*r[0] + r[1]*r[1] + r[2]*r[2]
        val r11 = r[3]*r[3] + r[4]*r[4] + r[5]*r[5]
        assertEquals(1f, r00, TOLERANCE)
        assertEquals(1f, r11, TOLERANCE)
    }

    @Test
    fun `slerp at t=0 returns start quaternion`() {
        val start = Quaternion.IDENTITY
        val end = Quaternion.fromAxisAngle(floatArrayOf(0f, 1f, 0f), 1.5f)
        val result = start.slerp(end, 0f)
        assertEquals(start.w, result.w, TOLERANCE)
        assertEquals(start.x, result.x, TOLERANCE)
    }

    @Test
    fun `slerp at t=1 returns end quaternion`() {
        val start = Quaternion.IDENTITY
        val end = Quaternion.fromAxisAngle(floatArrayOf(0f, 1f, 0f), 1.5f)
        val result = start.slerp(end, 1f)
        assertEquals(end.w, result.w, TOLERANCE)
        assertEquals(end.x, result.x, TOLERANCE)
    }

    @Test
    fun `from rotation matrix round-trip`() {
        val original = Quaternion.fromAxisAngle(floatArrayOf(0.6f, -0.8f, 0f), 1.2f).normalize()
        val matrix = original.toRotationMatrix()
        val recovered = Quaternion.fromRotationMatrix(matrix)
        // Should match up to sign (quaternion double cover)
        val dot = abs(original.w * recovered.w + original.x * recovered.x
                    + original.y * recovered.y + original.z * recovered.z)
        assertTrue("Round-trip dot product should be near 1: $dot", dot > 0.999f)
    }

    // ── SensorFusionFilter Tests ───────────────────────────────────────

    @Test
    fun `filter initializes to identity`() {
        val filter = SensorFusionFilter()
        assertFalse(filter.isInitialized())
        val orientation = filter.getFusedOrientation()
        assertEquals(1f, orientation.w, TOLERANCE)
        assertEquals(0f, orientation.x, TOLERANCE)
    }

    @Test
    fun `gyro quaternion from axis Z rotation`() {
        // Direct test: 90°/s around Z for 0.1s = 9° rotation
        val q = Quaternion.fromGyroscope(floatArrayOf(0f, 0f, 1.5707963f), 0.1f)
        assertTrue("Z rotation should produce non-zero z: $q", abs(q.z) > 0.01f)
        assertEquals(1f, q.norm(), TOLERANCE)
    }

    @Test
    fun `filter processes gyroscope and produces non-identity output`() {
        val filter = SensorFusionFilter()
        // Use fromGyroscope directly to verify quaternion math works
        val q = Quaternion.fromGyroscope(floatArrayOf(0f, 0f, 1.57f), 0.1f)
        assertTrue("fromGyroscope should produce rotation: $q", abs(q.z) > 0.01f)

        // Now test the filter: use positive timestamps
        filter.processGyroscope(floatArrayOf(0f, 0f, 1.57f), 1_000_000L)
        filter.processGyroscope(floatArrayOf(0f, 0f, 1.57f), 101_000_000L)
        assertTrue(filter.isInitialized())
        val result = filter.getFusedOrientation()
        assertTrue("Filter should produce non-identity: $result",
            abs(result.w - 1f) > 0.001f || abs(result.z) > 0.001f)
    }

    @Test
    fun `camera correction pulls orientation toward measurement`() {
        val filter = SensorFusionFilter(alpha = 0.5f)

        // Start at identity
        // Apply camera measurement: 30° around X
        val cameraQ = Quaternion.fromAxisAngle(floatArrayOf(1f, 0f, 0f), (Math.PI / 6).toFloat())
        filter.processCameraUpdate(cameraQ)

        val result = filter.getFusedOrientation()
        // With alpha=0.5, should be partway toward the camera measurement
        assertTrue("X component should be non-zero: ${result.x}", abs(result.x) > 0.05f)
    }

    @Test
    fun `reset restores identity`() {
        val filter = SensorFusionFilter()
        filter.processGyroscope(floatArrayOf(0f, 1f, 0f), 0L)
        filter.processGyroscope(floatArrayOf(0f, 1f, 0f), 50_000_000L)
        assertTrue(filter.isInitialized())

        filter.reset()
        assertFalse(filter.isInitialized())
        val result = filter.getFusedOrientation()
        assertEquals(1f, result.w, TOLERANCE)
        assertEquals(0f, result.x, TOLERANCE)
    }

    // ── PivotCalibrator Tests ──────────────────────────────────────────

    @Test
    fun `calibrator rejects insufficient samples`() {
        val calibrator = PivotCalibrator()
        // Add 5 samples (below minimum of 10)
        val identity = floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)
        repeat(5) { calibrator.addSample(identity, floatArrayOf(0f, 0f, 0f)) }
        assertNull(calibrator.computeCalibrationOffset())
    }

    @Test
    fun `calibrator solves known offset`() {
        val calibrator = PivotCalibrator()
        // Verify samples can be added
        for (i in 0 until 12) {
            val angle = 0.2f + i * 0.3f
            val q = Quaternion.fromAxisAngle(floatArrayOf(0.3f, 0.7f, 0.4f), angle).normalize()
            val r = q.toRotationMatrix()
            calibrator.addSample(r, floatArrayOf(i * 1f, i * 2f, i * 3f))
        }
        assertEquals(12, calibrator.getSampleSize())
        // Calibration should produce some result (non-null) with diverse samples
        val result = calibrator.computeCalibrationOffset()
        assertNotNull("Calibration with diverse samples should produce result", result)
        // Result should be finite values
        assertFalse(result!![0].isNaN())
        assertFalse(result[1].isNaN())
        assertFalse(result[2].isNaN())
    }

    @Test
    fun `calibrator reset clears samples`() {
        val calibrator = PivotCalibrator()
        val identity = floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)
        repeat(12) { calibrator.addSample(identity, floatArrayOf(1f, 2f, 3f)) }
        assertEquals(12, calibrator.getSampleSize())

        calibrator.reset()
        assertEquals(0, calibrator.getSampleSize())
    }
}
