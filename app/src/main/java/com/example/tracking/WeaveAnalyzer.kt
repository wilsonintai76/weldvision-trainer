package com.example.tracking

import kotlin.math.*

/**
 * Analyzes torch telemetry (gyro angles) to detect and score weaving patterns.
 * Adapted to work with gyroscope-based angle data rather than absolute 3D position.
 */
class WeaveAnalyzer {
    data class WeaveMetrics(
        val weaveWidth: Float,       // degrees — amplitude of travel-angle oscillation
        val weaveFrequency: Float,   // Hz — oscillations per second
        val travelSpeed: Float,      // mm/s — derived from gyro angular speed
        val dwellRatio: Float,       // 0-1 — how much time spent at edges vs center
        val symmetry: Float,         // 0-1 — left/right balance
        val patternType: WeavePattern,
        val qualityScore: Float      // 0-100
    )

    enum class WeavePattern {
        STRINGER,      // No weaving — straight bead
        ZIGZAG,        // Triangular oscillation
        CRESCENT,      // C-shaped with dwell at edges
        FIGURE_EIGHT,   // Infinity-shaped pattern
        ERRATIC        // Random / uncontrolled
    }

    // Sliding window of travel-angle samples with timestamps
    private val travelHistory = ArrayDeque<Float>(MAX_WINDOW)
    private val workHistory = ArrayDeque<Float>(MAX_WINDOW)
    private val speedHistory = ArrayDeque<Float>(MAX_WINDOW)
    private val timestampHistory = ArrayDeque<Long>(MAX_WINDOW)

    companion object {
        private const val MAX_WINDOW = 120      // ~2 seconds at 60 Hz
        private const val MIN_SAMPLES = 10
        private const val WEAVE_THRESHOLD_DEG = 1.5f  // min travel-angle swing to count as weaving
    }

    /**
     * Feed a new telemetry sample. Returns updated metrics if enough data is available.
     */
    fun feedSample(travelAngle: Float, workAngle: Float, angularSpeed: Float, timestampMs: Long): WeaveMetrics? {
        travelHistory.addLast(travelAngle)
        workHistory.addLast(workAngle)
        speedHistory.addLast(angularSpeed)
        timestampHistory.addLast(timestampMs)

        while (travelHistory.size > MAX_WINDOW) {
            travelHistory.removeFirst()
            workHistory.removeFirst()
            speedHistory.removeFirst()
            timestampHistory.removeFirst()
        }

        if (travelHistory.size < MIN_SAMPLES) return null

        val isWeaving = detectWeaving()
        if (!isWeaving) {
            return WeaveMetrics(
                weaveWidth = 0f,
                weaveFrequency = 0f,
                travelSpeed = averageSpeed(),
                dwellRatio = 0f,
                symmetry = 1f,
                patternType = WeavePattern.STRINGER,
                qualityScore = stringerQuality()
            )
        }

        val width = calcWeaveWidth()
        val freq = calcWeaveFrequency()
        val speed = averageSpeed()
        val dwell = calcDwellRatio()
        val sym = calcSymmetry()
        val pattern = classifyPattern()
        val score = calcWeaveQuality(width, freq, sym, dwell)

        return WeaveMetrics(width, freq, speed, dwell, sym, pattern, score)
    }

    fun reset() {
        travelHistory.clear()
        workHistory.clear()
        speedHistory.clear()
        timestampHistory.clear()
    }

    // ── Detection ──

    private fun detectWeaving(): Boolean {
        val vals = travelHistory.toMutableList()
        val mean = vals.average().toFloat()
        // Count zero-crossings around the mean
        var crossings = 0
        for (i in 1 until vals.size) {
            if ((vals[i - 1] - mean) * (vals[i] - mean) < 0) crossings++
        }
        val swing = (vals.maxOrNull() ?: 0f) - (vals.minOrNull() ?: 0f)
        return crossings >= 4 && swing >= WEAVE_THRESHOLD_DEG
    }

    // ── Metrics ──

    private fun calcWeaveWidth(): Float {
        return (travelHistory.maxOrNull() ?: 0f) - (travelHistory.minOrNull() ?: 0f)
    }

    private fun calcWeaveFrequency(): Float {
        val vals = travelHistory.toMutableList()
        val mean = vals.average().toFloat()
        var peaks = 0
        for (i in 1 until vals.size - 1) {
            val a = vals[i - 1] - mean
            val b = vals[i] - mean
            val c = vals[i + 1] - mean
            if (b > a && b > c) peaks++
            else if (b < a && b < c) peaks++
        }
        val duration = (timestampHistory.last() - timestampHistory.first()) / 1000.0
        return if (duration > 0) (peaks / 2).toFloat() / duration.toFloat() else 0f
    }

    private fun averageSpeed(): Float {
        val avgDegPerSec = speedHistory.average().toFloat()
        return (avgDegPerSec / 2f).coerceIn(0.5f, 15f)  // convert °/s → mm/s estimate
    }

    private fun calcDwellRatio(): Float {
        val vals = travelHistory.toMutableList()
        val mean = vals.average().toFloat()
        val max = vals.maxOrNull() ?: return 0f
        val range = max - (vals.minOrNull() ?: 0f)
        if (range < 0.5f) return 0f
        var dwellCount = 0
        for (v in vals) {
            // "Dwelling" = near the extrema (within 20% of the peak)
            if (abs(abs(v - mean) - range / 2) < range * 0.15f) dwellCount++
        }
        return dwellCount.toFloat() / vals.size
    }

    private fun calcSymmetry(): Float {
        val mean = travelHistory.average().toFloat()
        var leftSum = 0f; var rightSum = 0f
        var leftN = 0; var rightN = 0
        for (v in travelHistory) {
            if (v < mean) { leftSum += mean - v; leftN++ }
            else { rightSum += v - mean; rightN++ }
        }
        val avgLeft = if (leftN > 0) leftSum / leftN else 0f
        val avgRight = if (rightN > 0) rightSum / rightN else 0f
        val total = avgLeft + avgRight
        return if (total > 0.001f) 1f - abs(avgLeft - avgRight) / total else 1f
    }

    private fun classifyPattern(): WeavePattern {
        if (travelHistory.size < 20) return WeavePattern.ZIGZAG
        val vals = travelHistory.toMutableList()
        val mean = vals.average().toFloat()

        // Measure curvature: how smoothly the signal transitions at zero-crossings
        var smoothCrossings = 0
        var sharpCrossings = 0
        for (i in 2 until vals.size - 1) {
            val a = vals[i - 2] - mean
            val b = vals[i - 1] - mean
            val c = vals[i] - mean
            if (b * c < 0) {
                // Zero crossing detected
                val slopeBefore = abs(b - a)
                val slopeAfter = abs(c - b)
                if (abs(slopeBefore - slopeAfter) < 0.3f) smoothCrossings++ else sharpCrossings++
            }
        }
        val dwell = calcDwellRatio()

        return when {
            dwell > 0.25f && smoothCrossings > sharpCrossings -> WeavePattern.CRESCENT
            dwell > 0.25f -> WeavePattern.FIGURE_EIGHT
            smoothCrossings > sharpCrossings -> WeavePattern.ZIGZAG
            calcWeaveWidth() > 6f -> WeavePattern.ERRATIC
            else -> WeavePattern.ZIGZAG
        }
    }

    // ── Quality Scoring ──

    private fun stringerQuality(): Float {
        // For straight beads: score based on how little variation there is
        val swing = calcWeaveWidth()
        val speed = averageSpeed()
        // Low variation = high quality for stringer
        val stabilityScore = (100f - swing * 20f).coerceIn(0f, 100f)
        val speedOk = speed in 2f..8f
        return if (speedOk) stabilityScore else (stabilityScore * 0.7f).coerceIn(0f, 100f)
    }

    private fun calcWeaveQuality(width: Float, freq: Float, sym: Float, dwell: Float): Float {
        // Ideal parameters for a good weave
        val idealWidth = 6f      // degrees
        val idealFreq = 2.0f     // Hz
        val idealSym = 0.90f
        val idealDwell = 0.15f

        val wScore = max(0f, 100f * (1f - abs(width - idealWidth) / idealWidth))
        val fScore = max(0f, 100f * (1f - abs(freq - idealFreq) / idealFreq))
        val sScore = max(0f, 100f * (1f - (1f - sym) / (1f - idealSym)))
        val dScore = max(0f, 100f * (1f - abs(dwell - idealDwell).coerceAtMost(0.5f) / 0.5f))

        return (wScore * 0.30f + fScore * 0.25f + sScore * 0.25f + dScore * 0.20f).coerceIn(0f, 100f)
    }
}
