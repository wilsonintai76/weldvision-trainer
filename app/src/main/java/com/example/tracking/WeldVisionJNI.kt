package com.example.tracking

import android.util.Log
import java.nio.ByteBuffer

/**
 * WeldVisionJNI — Kotlin-side JNI bridge to the native AprilTag 3 tracker.
 *
 * Mirrors the C++ functions declared in apriltag_jni.cpp:
 *   Java_com_example_tracking_WeldVisionJNI_nativeDetectTags
 *   Java_com_example_tracking_WeldVisionJNI_nativeGetVersion
 *   Java_com_example_tracking_WeldVisionJNI_nativeSolvePivotCalibration
 *
 * All spatial values are in millimeters. Pose matrices follow the
 * homogeneous transform convention:
 *
 *   H_W^C = [ R_W^C  t_W^C ]
 *           [ 0 0 0    1   ]
 */
object WeldVisionJNI {

    private const val TAG = "WeldVisionJNI"

    /** Maximum radial distance (mm) for valid tip positions before outlier rejection. */
    const val MAX_TIP_RADIUS_MM = 500.0f

    // ── Native library loading ─────────────────────────────────────────

    init {
        try {
            System.loadLibrary("weldvision_jni")
            Log.d(TAG, "Native library loaded: ${nativeGetVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library weldvision_jni: ${e.message}")
            Log.e(TAG, "Run './gradlew assembleDebug' to compile the C++ sources.")
        }
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Runs AprilTag detection on a YUV_420_888 camera frame.
     *
     * @param yPlane        Direct ByteBuffer for the Y (luma) plane
     * @param uPlane        Direct ByteBuffer for the U (chroma) plane
     * @param vPlane        Direct ByteBuffer for the V (chroma) plane
     * @param width         Frame width in pixels
     * @param height        Frame height in pixels
     * @param yRowStride    Row stride of the Y plane
     * @param uvRowStride   Row stride of the U/V planes
     * @param uvPixelStride Pixel stride within U/V rows
     * @param fx            Camera intrinsic focal length X (px)
     * @param fy            Camera intrinsic focal length Y (px)
     * @param cx            Camera intrinsic principal point X (px)
     * @param cy            Camera intrinsic principal point Y (px)
     * @param tagSizeMeters Physical AprilTag side length in meters
     *
     * @return true if at least one valid (non-outlier) tag was detected
     */
    @JvmStatic
    external fun nativeDetectTags(
        yPlane: ByteBuffer,
        uPlane: ByteBuffer,
        vPlane: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        fx: Float,
        fy: Float,
        cx: Float,
        cy: Float,
        tagSizeMeters: Float,
    ): Int

    /**
     * Returns the native tracker library version string.
     */
    @JvmStatic
    external fun nativeGetVersion(): String

    /**
     * Solves for the static camera-to-tip bracket offset t_C^T via pivot
     * calibration. Requires at least 8 collected pose matrices.
     *
     * @param rotationMatrices    Flattened N × 9 floats (row-major 3×3 R)
     * @param translationVectors  Flattened N × 3 floats
     * @param numPoses            Number of collected calibration poses
     * @param outBracketOffset    Output: 3-element float array [t_x, t_y, t_z] in mm
     *
     * @return true if calibration converged
     */
    @JvmStatic
    external fun nativeSolvePivotCalibration(
        rotationMatrices: FloatArray,
        translationVectors: FloatArray,
        numPoses: Int,
        outBracketOffset: FloatArray,
    ): Int

    /**
     * Returns the latest valid detection's 4×4 homogeneous pose matrix.
     * Call this after nativeDetectTags() to retrieve H_W^C.
     *
     * @param outPose  FloatArray of size 16 (row-major 4×4)
     * @return 1 if a valid pose is available, 0 otherwise
     */
    @JvmStatic
    external fun nativeGetLatestPose(outPose: FloatArray): Int

    // ── Convenience Extensions ──────────────────────────────────────────

    /**
     * Checks whether a detected pose exceeds the 500 mm spatial envelope.
     *
     *   || t_W^C || <= 500.0 mm
     */
    @JvmStatic
    fun isOutlier(tx: Float, ty: Float, tz: Float): Boolean {
        return kotlin.math.sqrt(tx * tx + ty * ty + tz * tz) > MAX_TIP_RADIUS_MM
    }

    /**
     * Converts a 4×4 homogeneous transform (row-major, 16 floats) into
     * separate rotation (3×3) and translation (3) arrays for Kotlin usage.
     */
    @JvmStatic
    fun decomposeHomogeneous(
        H: FloatArray,
        outR: FloatArray = FloatArray(9),
        outT: FloatArray = FloatArray(3),
    ): Pair<FloatArray, FloatArray> {
        require(H.size == 16) { "H must be 16 floats (4×4 row-major)" }
        // Extract rotation: first 3 columns of first 3 rows
        for (r in 0..2) {
            for (c in 0..2) {
                outR[r * 3 + c] = H[r * 4 + c]
            }
        }
        // Extract translation: fourth column of first 3 rows
        outT[0] = H[3]
        outT[1] = H[7]
        outT[2] = H[11]
        return Pair(outR, outT)
    }

    /**
     * Extracts just the translation vector [tx, ty, tz] in mm from the
     * latest pose. Returns null if no pose is available.
     */
    @JvmStatic
    fun getLatestTranslation(): FloatArray? {
        val pose = FloatArray(16)
        if (nativeGetLatestPose(pose) == 0) return null
        return floatArrayOf(pose[3], pose[7], pose[11])
    }
}
