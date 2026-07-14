package com.example.tracking

import kotlin.math.*

/**
 * Hybrid Weld Tracker — fuses AprilTag positional tracking with gyroscope
 * angle tracking to provide complete 6-DOF weld simulation.
 *
 * Separates two distinct movement types:
 *   1. TILT/ANGLE  — torch orientation (gyroscope) — stays constant during straight weld
 *   2. TRAVEL       — movement along the weld joint (AprilTag position) — hand/arm movement
 *
 * The weld path is defined by two AprilTags placed at the start and end of the joint.
 */
class HybridWeldTracker {

    data class TrackerState(
        // ── Travel (AprilTag position) ──
        val travelProgress: Float = 0f,        // 0.0 – 1.0 along the weld joint
        val travelDistanceMm: Float = 0f,       // total mm traveled along joint
        val tagVisible: Boolean = false,        // AprilTag is currently tracked
        val tagTranslationMm: FloatArray = floatArrayOf(0f, 0f, 0f),  // [tx, ty, tz] in mm

        // ── Tilt/Angle (gyroscope) ──  
        val workAngle: Float = 90f,             // degrees — side-to-side torch tilt
        val travelAngle: Float = 10f,           // degrees — forward/back torch tilt
        val angularSpeed: Float = 0f,           // °/s — gyro angular velocity

        // ── Derived ──
        val travelSpeedMmPerSec: Float = 0f,    // mm/s along the joint
        val isTraveling: Boolean = false,       // actively moving along joint
        val isOnPath: Boolean = true            // within tolerance of the virtual path
    )

    // ── Weld path definition (world coordinates in mm) ──
    private var pathStartMm = floatArrayOf(0f, 0f, 0f)
    private var pathEndMm = floatArrayOf(100f, 0f, 0f)   // default 100mm joint
    private var isCalibrated = false

    // ── Travel tracking state ──
    private var lastTranslation = floatArrayOf(0f, 0f, 0f)
    private var lastTimestamp = 0L
    private var totalTravelMm = 0f

    /**
     * Calibrate the weld path from two AprilTag world positions.
     * Call this once after the calibration sweep routine.
     *
     * @param startTagWorld  World translation of tag at joint start (mm)
     * @param endTagWorld    World translation of tag at joint end (mm)
     */
    fun calibratePath(startTagWorld: FloatArray, endTagWorld: FloatArray) {
        pathStartMm = startTagWorld.clone()
        pathEndMm = endTagWorld.clone()
        isCalibrated = true
        reset()
    }

    /**
     * Set a manual path (e.g., from training module definition).
     */
    fun setManualPath(startMm: FloatArray, endMm: FloatArray) {
        pathStartMm = startMm.clone()
        pathEndMm = endMm.clone()
        isCalibrated = true
        reset()
    }

    fun reset() {
        lastTranslation = floatArrayOf(0f, 0f, 0f)
        lastTimestamp = 0L
        totalTravelMm = 0f
    }

    /**
     * Main update — call every frame (CameraX ImageAnalysis callback).
     *
     * @param tagTranslationMm  Latest AprilTag world translation [tx, ty, tz] in mm, or null
     * @param workAngle         Gyro work angle (degrees)
     * @param travelAngle       Gyro travel angle (degrees)
     * @param angularSpeed      Gyro angular velocity (°/s)
     */
    fun update(
        tagTranslationMm: FloatArray?,
        workAngle: Float,
        travelAngle: Float,
        angularSpeed: Float
    ): TrackerState {
        val now = System.currentTimeMillis()
        val tagVisible = tagTranslationMm != null
        val tx = tagTranslationMm ?: lastTranslation

        // ── Travel progress: project current position onto the weld path ──
        val pathVec = floatArrayOf(
            pathEndMm[0] - pathStartMm[0],
            pathEndMm[1] - pathStartMm[1],
            pathEndMm[2] - pathStartMm[2]
        )
        val pathLen = sqrt(pathVec[0] * pathVec[0] + pathVec[1] * pathVec[1] + pathVec[2] * pathVec[2])
        val progress = if (isCalibrated && pathLen > 0.1f) {
            val posRelative = floatArrayOf(
                tx[0] - pathStartMm[0],
                tx[1] - pathStartMm[1],
                tx[2] - pathStartMm[2]
            )
            // Project onto path direction
            val dot = posRelative[0] * pathVec[0] + posRelative[1] * pathVec[1] + posRelative[2] * pathVec[2]
            (dot / (pathLen * pathLen)).coerceIn(0f, 1f)
        } else {
            0f
        }

        // ── Travel speed ──
        val dt = if (lastTimestamp > 0) (now - lastTimestamp) / 1000.0 else 0.0
        val speed = if (dt > 0.001 && tagVisible) {
            val dx = tx[0] - lastTranslation[0]
            val dy = tx[1] - lastTranslation[1]
            val dz = tx[2] - lastTranslation[2]
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            (dist / dt).toFloat()
        } else 0f

        // ── Accumulate total travel (only when moving forward along joint) ──
        val isMovingForward = progress > (if (tagVisible) totalTravelMm / pathLen.coerceAtLeast(1f) else 0f)
        val movement = if (tagVisible && dt > 0.001 && isMovingForward) {
            val dx = tx[0] - lastTranslation[0]
            val dy = tx[1] - lastTranslation[1]
            val dz = tx[2] - lastTranslation[2]
            sqrt(dx * dx + dy * dy + dz * dz)
        } else 0f

        totalTravelMm = (totalTravelMm + movement).coerceAtMost(pathLen)

        // ── Path tolerance check ──
        val onPath = if (isCalibrated && pathLen > 0.1f && tagVisible) {
            val posRelative = floatArrayOf(tx[0] - pathStartMm[0], tx[1] - pathStartMm[1], tx[2] - pathStartMm[2])
            val dot = posRelative[0] * pathVec[0] + posRelative[1] * pathVec[1] + posRelative[2] * pathVec[2]
            val proj = dot / pathLen
            val closestX = pathStartMm[0] + pathVec[0] * proj / pathLen
            val closestY = pathStartMm[1] + pathVec[1] * proj / pathLen
            val closestZ = pathStartMm[2] + pathVec[2] * proj / pathLen
            val perpDist = sqrt(
                (tx[0] - closestX) * (tx[0] - closestX) +
                (tx[1] - closestY) * (tx[1] - closestY) +
                (tx[2] - closestZ) * (tx[2] - closestZ)
            )
            perpDist < 15f  // 15mm tolerance from path centerline
        } else !isCalibrated

        // ── Store for next frame ──
        lastTranslation = tx.clone()
        lastTimestamp = now

        return TrackerState(
            travelProgress = progress,
            travelDistanceMm = totalTravelMm,
            tagVisible = tagVisible,
            tagTranslationMm = tx.clone(),
            workAngle = workAngle,
            travelAngle = travelAngle,
            angularSpeed = angularSpeed,
            travelSpeedMmPerSec = speed,
            isTraveling = tagVisible && progress > 0.01f && progress < 0.99f && speed > 0.5f,
            isOnPath = onPath
        )
    }

    /** Path length in mm. */
    fun pathLengthMm(): Float {
        val dx = pathEndMm[0] - pathStartMm[0]
        val dy = pathEndMm[1] - pathStartMm[1]
        val dz = pathEndMm[2] - pathStartMm[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
