package com.example.tracking.math

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rigid-body pivot calibrator that solves for the static torch tip offset
 * vector t_C^T (camera-to-tip) using least-squares optimization.
 *
 * During a pivot sweep, the torch tip is held at a fixed point c_W while
 * the handle is rotated. This yields N camera frames:
 *   R_{W,i}^C · t_C^T + t_{W,i}^C = c_W   (constant)
 *
 * Rearranging to solve for t_C^T:
 *   A · t_C^T = B
 *
 * where:
 *   A = Σ (I - R_{W,i}^C)
 *   B = Σ (t_{W,i}^C - t_{W,1}^C)
 *
 * Solved via 3×3 Cramer's rule (low CPU, no external dependencies).
 *
 * Usage:
 *   val calibrator = PivotCalibrator()
 *   calibrator.addSample(rotation3x3, translation3)
 *   // ... collect 10+ samples during sweep ...
 *   val offset = calibrator.computeCalibrationOffset() // [tx, ty, tz] in mm
 */
class PivotCalibrator {

    private val rotationSamples = ArrayList<FloatArray>()    // N × 9 (3×3 row-major)
    private val translationSamples = ArrayList<FloatArray>() // N × 3 [x, y, z]

    /** Minimum number of spatially diverse samples required for a valid solve. */
    val minSamples: Int = 10

    /**
     * Adds a calibration sample from a single camera frame.
     *
     * @param rotationMatrix 3×3 rotation matrix in row-major order (9 floats)
     * @param translation    Camera translation vector [tx, ty, tz] in mm
     */
    fun addSample(rotationMatrix: FloatArray, translation: FloatArray) {
        require(rotationMatrix.size == 9) { "rotationMatrix must be 9 elements (3×3 row-major)" }
        require(translation.size == 3) { "translation must be 3 elements [tx, ty, tz]" }
        rotationSamples.add(rotationMatrix.clone())
        translationSamples.add(translation.clone())
    }

    /** Number of collected samples. */
    fun getSampleSize(): Int = rotationSamples.size

    /** Clears all collected samples. */
    fun reset() {
        rotationSamples.clear()
        translationSamples.clear()
    }

    /**
     * Computes the least-squares optimal offset vector t_C^T.
     *
     * @return 3-element float array [tx, ty, tz] in mm, or null if insufficient
     *         samples or singular matrix (poses not diverse enough)
     */
    fun computeCalibrationOffset(): FloatArray? {
        val n = rotationSamples.size
        if (n < minSamples) return null

        // Build A = Σ (I - R_i) and B = Σ (t_i - t_0)
        var a00 = 0f; var a01 = 0f; var a02 = 0f
        var a10 = 0f; var a11 = 0f; var a12 = 0f
        var a20 = 0f; var a21 = 0f; var a22 = 0f
        var b0 = 0f; var b1 = 0f; var b2 = 0f

        val t0 = translationSamples[0]

        for (i in 0 until n) {
            val r = rotationSamples[i]
            val t = translationSamples[i]

            // I - R (row 0)
            a00 += 1f - r[0]
            a01 += -r[1]
            a02 += -r[2]
            // I - R (row 1)
            a10 += -r[3]
            a11 += 1f - r[4]
            a12 += -r[5]
            // I - R (row 2)
            a20 += -r[6]
            a21 += -r[7]
            a22 += 1f - r[8]

            // t_i - t_0
            b0 += t[0] - t0[0]
            b1 += t[1] - t0[1]
            b2 += t[2] - t0[2]
        }

        // Solve 3×3 system A·x = B via Cramer's rule
        val det = a00 * (a11 * a22 - a12 * a21)
                - a01 * (a10 * a22 - a12 * a20)
                + a02 * (a10 * a21 - a11 * a20)

        if (abs(det) < 1e-6f) return null // Singular — poses are coplanar

        val detX = b0 * (a11 * a22 - a12 * a21)
                 - a01 * (b1 * a22 - a12 * b2)
                 + a02 * (b1 * a21 - a11 * b2)

        val detY = a00 * (b1 * a22 - a12 * b2)
                 - b0 * (a10 * a22 - a12 * a20)
                 + a02 * (a10 * b2 - b1 * a20)

        val detZ = a00 * (a11 * b2 - b1 * a21)
                 - a01 * (a10 * b2 - b1 * a20)
                 + b0 * (a10 * a21 - a11 * a20)

        return floatArrayOf(detX / det, detY / det, detZ / det)
    }

    /**
     * Estimates the condition number of the accumulated matrix A
     * as a proxy for sample diversity quality.
     *
     * @return Condition number estimate (lower is better), or -1 if no samples
     */
    fun estimateConditionNumber(): Float {
        val n = rotationSamples.size
        if (n < 3) return -1f

        // Build A same as computeCalibrationOffset
        var a00 = 0f; var a01 = 0f; var a02 = 0f
        var a10 = 0f; var a11 = 0f; var a12 = 0f
        var a20 = 0f; var a21 = 0f; var a22 = 0f
        for (i in 0 until n) {
            val r = rotationSamples[i]
            a00 += 1f - r[0]; a01 += -r[1]; a02 += -r[2]
            a10 += -r[3]; a11 += 1f - r[4]; a12 += -r[5]
            a20 += -r[6]; a21 += -r[7]; a22 += 1f - r[8]
        }

        // Frobenius norm as simple condition estimate
        val normA = sqrt(a00 * a00 + a01 * a01 + a02 * a02
                       + a10 * a10 + a11 * a11 + a12 * a12
                       + a20 * a20 + a21 * a21 + a22 * a22)
        if (normA < 1e-6f) return -1f

        // Trace-based inverse norm estimate (coarse but fast)
        val trace = a00 + a11 + a22
        return if (abs(trace) > 1e-6f) normA / abs(trace) else -1f
    }
}
