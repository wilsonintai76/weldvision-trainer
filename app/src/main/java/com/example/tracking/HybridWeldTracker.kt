package com.example.tracking

import kotlin.math.*
import com.example.tracking.math.Quaternion
import com.example.tracking.math.SensorFusionFilter

/**
 * Hybrid Weld Tracker — fuses AprilTag positional tracking with gyroscope
 * angle tracking to provide complete 6-DOF weld simulation.
 *
 * Separates two distinct movement types:
 *   1. TILT/ANGLE  — torch orientation (gyroscope) — stays constant during straight weld
 *   2. TRAVEL       — movement along the weld joint (AprilTag position) — hand/arm movement
 *
 * The weld path is defined as a fixed spatial offset relative to the rigid fixture frame. 
 * The fixture frame's World Origin [W] is established by its permanently mounted AprilTags.
 */
class HybridWeldTracker {

    data class TrackerState(
        // ── Travel (AprilTag position) ──
        val travelProgress: Float = 0f,        // 0.0 – 1.0 along the weld joint
        val travelDistanceMm: Float = 0f,       // total mm traveled along joint
        val tagVisible: Boolean = false,        // AprilTag is currently tracked
        val tagTranslationMm: FloatArray = floatArrayOf(0f, 0f, 0f),  // [tx, ty, tz] in mm
        val tipTranslationMm: FloatArray = floatArrayOf(0f, 0f, 0f),  // tip [tx, ty, tz] in mm


        // ── Tilt/Angle (gyroscope) ──  
        val workAngle: Float = 90f,             // degrees — side-to-side torch tilt
        val travelAngle: Float = 10f,           // degrees — forward/back torch tilt
        val angularSpeed: Float = 0f,           // °/s — gyro angular velocity

        // ── Derived ──
        val travelSpeedMmPerSec: Float = 0f,    // mm/s along the joint
        val tipSpeedMmPerSec: Float = 0f,       // mm/s of the tip
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
    
    // ── Sensor Fusion ──
    private val fusionFilter = SensorFusionFilter()

    
    // ── Tip Offset ──
    private var tipOffsetMm = floatArrayOf(0f, 0f, 0f)
    
    fun setTipOffset(offset: FloatArray) {
        tipOffsetMm = offset.clone()
    }

    /**
     * Set the weld path coordinates relative to the fixture frame's World Origin.
     * Call this once the fixture has been detected to establish the joint's location.
     *
     * @param startWorld  World translation of the joint start (mm) relative to fixture origin
     * @param endWorld    World translation of the joint end (mm) relative to fixture origin
     */
    fun setFixturePathOffset(startWorld: FloatArray, endWorld: FloatArray) {
        pathStartMm = startWorld.clone()
        pathEndMm = endWorld.clone()
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
        fusionFilter.reset()
    }

    /**
     * Integrates high-frequency IMU gyroscope data.
     * @param gyroData [wx, wy, wz] in rad/s
     * @param timestamp System nanoseconds
     */
    fun processGyroscope(gyroData: FloatArray, timestamp: Long) {
        fusionFilter.processGyroscope(gyroData, timestamp)
    }

    /**
     * Main update — call every frame (CameraX ImageAnalysis callback).
     *
     * @param tagTranslationMm  Latest AprilTag world translation [tx, ty, tz] in mm, or null
     * @param cameraRotation    Latest AprilTag world orientation (Quaternion), or null
     */
    fun update(
        tagTranslationMm: FloatArray?,
        cameraRotation: Quaternion?
    ): TrackerState {
        val now = System.currentTimeMillis()
        val tagVisible = tagTranslationMm != null
        val tx = tagTranslationMm ?: lastTranslation
        
        // Update orientation with low-frequency camera data
        if (cameraRotation != null) {
            fusionFilter.processCameraUpdate(cameraRotation)
        }
        
        val fusedOrientation = fusionFilter.getFusedOrientation()
        val eulerAngles = fusedOrientation.toEuler()
        
        // Use standard Euler extraction for UI angles (assuming Roll = Work, Pitch = Travel)
        // Convert from radians to degrees
        val workAngle = Math.toDegrees(eulerAngles[0].toDouble()).toFloat()
        val travelAngle = Math.toDegrees(eulerAngles[1].toDouble()).toFloat()

        // ── 3D Tip Offset calculation (World = Tag + R * Offset) ──
        val R = fusedOrientation.toRotationMatrix()
        // Offset is in camera frame, R rotates it to world frame
        val rx = R[0]*tipOffsetMm[0] + R[1]*tipOffsetMm[1] + R[2]*tipOffsetMm[2]
        val ry = R[3]*tipOffsetMm[0] + R[4]*tipOffsetMm[1] + R[5]*tipOffsetMm[2]
        val rz = R[6]*tipOffsetMm[0] + R[7]*tipOffsetMm[1] + R[8]*tipOffsetMm[2]
        
        val tipTx = floatArrayOf(
            tx[0] + rx,
            tx[1] + ry,
            tx[2] + rz
        )

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
        val tipSpeed = if (dt > 0.001 && tagVisible) {
            val dx = tipTx[0] - lastTranslation[0]
            val dy = tipTx[1] - lastTranslation[1]
            val dz = tipTx[2] - lastTranslation[2]
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            (dist / dt).toFloat()
        } else 0f
        
        val speed = tipSpeed // Can be separated later if path projection speed is needed

        // ── Accumulate total travel (only when moving forward along joint) ──
        val isMovingForward = progress > (if (tagVisible) totalTravelMm / pathLen.coerceAtLeast(1f) else 0f)
        val movement = if (tagVisible && dt > 0.001 && isMovingForward) {
            val dx = tipTx[0] - lastTranslation[0]
            val dy = tipTx[1] - lastTranslation[1]
            val dz = tipTx[2] - lastTranslation[2]
            sqrt(dx * dx + dy * dy + dz * dz)
        } else 0f

        totalTravelMm = (totalTravelMm + movement).coerceAtMost(pathLen.coerceAtLeast(1f))

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
        lastTranslation = tipTx.clone()
        lastTimestamp = now

        return TrackerState(
            travelProgress = progress,
            travelDistanceMm = totalTravelMm,
            tagVisible = tagVisible,
            tagTranslationMm = tx.clone(),
            tipTranslationMm = tipTx.clone(),
            workAngle = workAngle,
            travelAngle = travelAngle,
            angularSpeed = 0f, // Angular speed could be computed from gyro magnitude if needed

            travelSpeedMmPerSec = speed,
            tipSpeedMmPerSec = tipSpeed,
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
