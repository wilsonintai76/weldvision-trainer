/**
 * apriltag_jni.cpp — WeldVision Trainer 2.0 JNI Bridge
 * ======================================================
 *
 * Bridges the high-performance C++ AprilTag 3 tracking library into
 * Kotlin/Java via JNI. Receives raw CameraX YUV_420_888 frame bytes,
 * runs AprilTag detection + PnP pose estimation, and returns the
 * homogeneous transformation matrix H_W^C for each detected tag.
 *
 * JNI class target: com.example.tracking.WeldVisionJNI
 *
 * H_W^C = [ R_W^C  t_W^C ]
 *         [ 0 0 0    1   ]
 *
 * where:
 *   - R_W^C is the 3×3 rotation from camera frame [C] to world frame [W]
 *   - t_W^C is the 3×1 translation vector in millimeters
 */

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstring>
#include <vector>
#include <cmath>
#include <mutex>

#define LOG_TAG "WeldVisionJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── AprilTag 3 headers ────────────────────────────────────────────────
extern "C" {
#include "apriltag/apriltag.h"
#include "apriltag/apriltag_pose.h"
#include "apriltag/tagStandard41h12.h"
#include "apriltag/tagStandard52h13.h"
#include "apriltag/tagCustom48h12.h"
#include "apriltag/common/image_u8.h"
}

// ============================================================================
// 1. Data Structures
// ============================================================================

/**
 * A single detected tag with its full 4×4 homogeneous pose matrix.
 * All translation values are in millimeters.
 */
struct DetectedTag {
    int id;                  // Tag family ID (e.g. 0 for tagStandard41h12)
    int hamming;             // Hamming distance (0 = perfect match)
    float decision_margin;   // Detection confidence (higher is better)
    float H[16];             // 4×4 homogeneous transform, row-major
    float center[2];         // Image-space center (px)
    float corners[8];        // Image-space corners [x0,y0, x1,y1, x2,y2, x3,y3]
};

// ============================================================================
// 2. Internal Helpers
// ============================================================================

/**
 * Computes the Euclidean norm of the translation component of H.
 * Used to apply the 500.0 mm spatial outlier boundary filter:
 *
 *   || p_W_tip(t) || <= 500.0 mm
 */
static float translationNorm(const float H[16]) {
    const float tx = H[3];   // translation X
    const float ty = H[7];   // translation Y
    const float tz = H[11];  // translation Z
    return std::sqrt(tx * tx + ty * ty + tz * tz);
}

/**
 * Identity 4×4 matrix.
 */
static void identityMatrix(float H[16]) {
    std::memset(H, 0, 16 * sizeof(float));
    H[0] = H[5] = H[10] = H[15] = 1.0f;
}

/** Spatial outlier envelope: || p_W_tip(t) || <= 500.0 mm */
static constexpr float MAX_TIP_RADIUS_MM = 500.0f;

// ============================================================================
// 3. Singleton Detector (created once, reused across frames)
// ============================================================================

/** Global detector instance with mutex guard for thread safety. */
static apriltag_detector_t *g_detector = nullptr;
static apriltag_family_t *g_family41h12 = nullptr;
static apriltag_family_t *g_family52h13 = nullptr;
static apriltag_family_t *g_familyCustom48 = nullptr;
static std::mutex g_detectorMutex;

/** Latest valid detection pose (4×4 row-major, protected by g_detectorMutex). */
static float g_latestPose[16] = {0};
static bool g_hasLatestPose = false;

static void ensureDetectorCreated() {
    std::lock_guard<std::mutex> lock(g_detectorMutex);
    if (g_detector) return;

    g_family41h12 = tagStandard41h12_create();
    g_family52h13 = tagStandard52h13_create();
    g_familyCustom48 = tagCustom48h12_create();

    g_detector = apriltag_detector_create();
    apriltag_detector_add_family(g_detector, g_family41h12);
    apriltag_detector_add_family(g_detector, g_family52h13);
    apriltag_detector_add_family(g_detector, g_familyCustom48);

    // Tune for welding environment: lower quad decimation for sub-pixel
    // accuracy on small tags; higher refine edges for metal reflections.
    g_detector->quad_decimate = 1.0f;   // No decimation — full resolution
    g_detector->quad_sigma = 0.0f;      // No blur (CameraX already focuses)
    g_detector->nthreads = 2;           // Use 2 threads for quad decoding
    g_detector->refine_edges = 1;       // Sub-pixel edge refinement ON
    g_detector->decode_sharpening = 0.25;

    LOGD("ensureDetectorCreated: AprilTag 3 detector initialized "
         "(families: 41h12, 52h13, Custom48h12).");
}

// ============================================================================
// 4. YUV → Grayscale Conversion
// ============================================================================

/**
 * Extracts the Y (luma) plane from a YUV_420_888 frame into an
 * AprilTag-compatible image_u8_t. Handles row stride padding —
 * CameraX may allocate buffers wider than the logical frame width.
 */
static image_u8_t *yuvToGrayscale(
    const uint8_t *yPlane,
    int width,
    int height,
    int yRowStride)
{
    image_u8_t *im = image_u8_create(width, height);
    if (!im) return nullptr;

    for (int row = 0; row < height; row++) {
        const uint8_t *src = yPlane + row * yRowStride;
        uint8_t *dst = im->buf + row * im->stride;
        // Copy only the logical width; ignore stride padding bytes
        memcpy(dst, src, width);
    }

    return im;
}

// ============================================================================
// 5. Real AprilTag 3 Detection + Pose Estimation
// ============================================================================

/**
 * Full detection pipeline:
 *  1. Convert YUV → grayscale image_u8
 *  2. Run apriltag_detector_detect()
 *  3. For each detection, estimate 6-DOF pose via PnP
 *  4. Pack results into DetectedTag structs
 *  5. Apply ||p|| <= 500 mm spatial outlier filter
 *
 * All translation values are in millimeters.
 */
static std::vector<DetectedTag> detectTags(
    const uint8_t *yPlane,
    const uint8_t * /*uPlane*/,
    const uint8_t * /*vPlane*/,
    int width,
    int height,
    int yRowStride,
    int /*uvRowStride*/,
    float fx,
    float fy,
    float cx,
    float cy,
    float tagSizeMeters)
{
    std::vector<DetectedTag> results;

    ensureDetectorCreated();
    if (!g_detector) {
        LOGE("detectTags: Detector not initialized.");
        return results;
    }

    // ── Step 1: YUV → Grayscale ────────────────────────────────────
    image_u8_t *im = yuvToGrayscale(yPlane, width, height, yRowStride);
    if (!im) {
        LOGE("detectTags: Failed to allocate grayscale image.");
        return results;
    }

    // ── Step 2: Run AprilTag detection ─────────────────────────────
    zarray_t *detections = apriltag_detector_detect(g_detector, im);
    if (!detections || zarray_size(detections) == 0) {
        image_u8_destroy(im);
        LOGD("detectTags: No tags detected in frame.");
        return results;
    }

    const int tagCount = zarray_size(detections);
    LOGD("detectTags: %d raw detections found.", tagCount);

    // ── Step 3: Pose estimation per detection ──────────────────────
    for (int i = 0; i < tagCount; i++) {
        apriltag_detection_t *det = nullptr;
        zarray_get(detections, i, &det);
        if (!det) continue;

        // Build pose estimation info
        apriltag_detection_info_t info;
        info.det = det;
        info.tagsize = tagSizeMeters;  // meters
        info.fx = fx;
        info.fy = fy;
        info.cx = cx;
        info.cy = cy;

        apriltag_pose_t pose;
        double err = estimate_tag_pose(&info, &pose);

        if (err < 0) {
            LOGD("detectTags: Pose estimation failed for tag %d (err=%.2f).",
                 det->id, err);
            continue;
        }

        // ── Step 4: Pack into DetectedTag ─────────────────────────
        DetectedTag tag;
        tag.id = det->id;
        tag.hamming = det->hamming;
        tag.decision_margin = static_cast<float>(det->decision_margin);

        // Copy 3×3 rotation R (row-major in matd) into 4×4 H
        identityMatrix(tag.H);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                tag.H[r * 4 + c] = static_cast<float>(
                    matd_get(pose.R, r, c));
            }
        }
        // Copy translation t (3×1) — convert meters → millimeters
        tag.H[3]  = static_cast<float>(matd_get(pose.t, 0, 0)) * 1000.0f;
        tag.H[7]  = static_cast<float>(matd_get(pose.t, 1, 0)) * 1000.0f;
        tag.H[11] = static_cast<float>(matd_get(pose.t, 2, 0)) * 1000.0f;

        // Image-space coordinates
        tag.center[0] = static_cast<float>(det->c[0]);
        tag.center[1] = static_cast<float>(det->c[1]);
        for (int j = 0; j < 4; j++) {
            tag.corners[j * 2]     = static_cast<float>(det->p[j][0]);
            tag.corners[j * 2 + 1] = static_cast<float>(det->p[j][1]);
        }

        // ── Step 5: Spatial outlier check ─────────────────────────
        const float norm = translationNorm(tag.H);
        if (norm <= MAX_TIP_RADIUS_MM) {
            results.push_back(tag);
        } else {
            LOGD("detectTags: Outlier dropped — tag %d at %.1f mm "
                 "(> %.0f mm envelope).",
                 tag.id, norm, MAX_TIP_RADIUS_MM);
        }

        // Free pose matrices
        matd_destroy(pose.R);
        matd_destroy(pose.t);
    }

    // ── Cleanup ───────────────────────────────────────────────────
    apriltag_detections_destroy(detections);
    image_u8_destroy(im);

    LOGD("detectTags: %zu valid detections after outlier filter.",
         results.size());

    // ── Cache latest pose for Kotlin retrieval ─────────────────────
    {
        std::lock_guard<std::mutex> lock(g_detectorMutex);
        if (!results.empty()) {
            const auto &best = results[0];  // lowest-ID or first valid
            std::memcpy(g_latestPose, best.H, 16 * sizeof(float));
            g_hasLatestPose = true;
        } else {
            g_hasLatestPose = false;
        }
    }

    return results;
}

// ============================================================================
// 6. JNI Exports
// ============================================================================

extern "C" {

/**
 * Detect AprilTags in a YUV_420_888 camera frame and return their 6-DOF poses.
 *
 * This is called from Kotlin on every CameraX ImageAnalysis frame (~30 Hz).
 * The detector singleton is reused; only the Y→grayscale conversion and
 * quad detection run per frame.
 *
 * @param yPlane        Direct ByteBuffer — Y (luma) plane
 * @param uPlane        Direct ByteBuffer — U (chroma) plane
 * @param vPlane        Direct ByteBuffer — V (chroma) plane
 * @param width         Frame width in pixels
 * @param height        Frame height in pixels
 * @param yRowStride    Row stride of the Y plane
 * @param uvRowStride   Row stride of the U/V planes
 * @param uvPixelStride Pixel stride within U/V rows
 * @param fx            Camera intrinsic focal length X (pixels)
 * @param fy            Camera intrinsic focal length Y (pixels)
 * @param cx            Camera intrinsic principal point X (pixels)
 * @param cy            Camera intrinsic principal point Y (pixels)
 * @param tagSizeMeters Physical side length of the AprilTag (meters)
 *
 * @return Number of valid (non-outlier) tags detected this frame
 */
JNIEXPORT jint JNICALL
Java_com_example_tracking_WeldVisionJNI_nativeDetectTags(
    JNIEnv *env,
    jobject /*thiz*/,
    jobject yPlane,
    jobject uPlane,
    jobject vPlane,
    jint width,
    jint height,
    jint yRowStride,
    jint uvRowStride,
    jint uvPixelStride,
    jfloat fx,
    jfloat fy,
    jfloat cx,
    jfloat cy,
    jfloat tagSizeMeters)
{
    // Lock the direct byte buffers
    auto *yData = static_cast<uint8_t *>(env->GetDirectBufferAddress(yPlane));
    auto *uData = static_cast<uint8_t *>(env->GetDirectBufferAddress(uPlane));
    auto *vData = static_cast<uint8_t *>(env->GetDirectBufferAddress(vPlane));

    if (!yData) {
        LOGE("nativeDetectTags: Failed to lock Y plane buffer.");
        return 0;
    }
    // U/V planes may be null for certain YUV formats — that's OK since
    // we only extract the Y (luma) channel for grayscale detection.

    // Run the real AprilTag 3 pipeline
    std::vector<DetectedTag> detections = detectTags(
        yData, uData, vData,
        width, height,
        yRowStride, uvRowStride,
        fx, fy, cx, cy,
        tagSizeMeters);

    return static_cast<jint>(detections.size());
}

/**
 * Retrieves the native library version string.
 */
JNIEXPORT jstring JNICALL
Java_com_example_tracking_WeldVisionJNI_nativeGetVersion(
    JNIEnv *env,
    jobject /*thiz*/)
{
    return env->NewStringUTF("WeldVision Tracker v2.0.0 (AprilTag 3 live)");
}

/**
 * Pivot calibration solver — computes the static camera-to-tip bracket
 * offset t_C^T by minimizing reprojection error across N collected poses:
 *
 *   argmin_t Σ_i || R_i * t_C^T + t_i - p_pivot ||²
 *
 * This is solved via the normal equations AᵀA x = Aᵀb where:
 *   A = [R_0; R_1; ...; R_{N-1}]   (3N × 3 stacked rotation blocks)
 *   b = [-t_0; -t_1; ...; -t_{N-1}] (3N × 1 negated translations)
 *
 * @return 1 if calibration converged, 0 otherwise
 */
JNIEXPORT jint JNICALL
Java_com_example_tracking_WeldVisionJNI_nativeSolvePivotCalibration(
    JNIEnv *env,
    jobject /*thiz*/,
    jfloatArray rotationMatrices,
    jfloatArray translationVectors,
    jint numPoses,
    jfloatArray outBracketOffset)
{
    jfloat *R = env->GetFloatArrayElements(rotationMatrices, nullptr);
    jfloat *t = env->GetFloatArrayElements(translationVectors, nullptr);
    jfloat *out = env->GetFloatArrayElements(outBracketOffset, nullptr);

    if (!R || !t || !out || numPoses < 8) {
        LOGE("nativeSolvePivotCalibration: Need >= 8 poses (got %d).", numPoses);
        if (R) env->ReleaseFloatArrayElements(rotationMatrices, R, JNI_ABORT);
        if (t) env->ReleaseFloatArrayElements(translationVectors, t, JNI_ABORT);
        if (out) env->ReleaseFloatArrayElements(outBracketOffset, out, 0);
        return 0;
    }

    // Build normal equations: AᵀA (3×3) and Aᵀb (3×1)
    // A = stacked 3×3 rotation blocks, b = stacked -t vectors
    double AtA[9] = {0};  // 3×3, row-major
    double Atb[3] = {0};  // 3×1

    for (int i = 0; i < numPoses; i++) {
        const float *Ri = R + i * 9;   // 3×3 row-major rotation
        const float *ti = t + i * 3;   // [tx, ty, tz]

        // Accumulate AᵀA += R_iᵀ * R_i
        // Since R_i is orthonormal, R_iᵀ * R_i = I, so AtA accumulates to N*I.
        // We keep the general form for robustness against near-singular R.
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                double dot = 0.0;
                for (int k = 0; k < 3; k++) {
                    dot += static_cast<double>(Ri[k * 3 + r]) *
                           static_cast<double>(Ri[k * 3 + c]);
                }
                AtA[r * 3 + c] += dot;
            }
        }

        // Accumulate Aᵀb += R_iᵀ * (-t_i)
        for (int r = 0; r < 3; r++) {
            double dot = 0.0;
            for (int k = 0; k < 3; k++) {
                dot += static_cast<double>(Ri[k * 3 + r]) *
                       static_cast<double>(-ti[k]);
            }
            Atb[r] += dot;
        }
    }

    // Solve 3×3 system AtA * x = Atb via Cramer's rule (fast for 3×3)
    double det = AtA[0] * (AtA[4] * AtA[8] - AtA[5] * AtA[7])
               - AtA[1] * (AtA[3] * AtA[8] - AtA[5] * AtA[6])
               + AtA[2] * (AtA[3] * AtA[7] - AtA[4] * AtA[6]);

    if (std::abs(det) < 1e-9) {
        LOGE("nativeSolvePivotCalibration: Singular matrix — "
             "poses may be co-planar. Collect more varied poses.");
        env->ReleaseFloatArrayElements(rotationMatrices, R, JNI_ABORT);
        env->ReleaseFloatArrayElements(translationVectors, t, JNI_ABORT);
        env->ReleaseFloatArrayElements(outBracketOffset, out, 0);
        return 0;
    }

    double invDet = 1.0 / det;
    out[0] = static_cast<float>(invDet * (
        Atb[0] * (AtA[4] * AtA[8] - AtA[5] * AtA[7]) +
        AtA[1] * (AtA[5] * Atb[2] - Atb[1] * AtA[8]) +
        AtA[2] * (Atb[1] * AtA[7] - AtA[4] * Atb[2])));

    out[1] = static_cast<float>(invDet * (
        AtA[0] * (Atb[1] * AtA[8] - AtA[5] * Atb[2]) +
        Atb[0] * (AtA[5] * AtA[6] - AtA[3] * AtA[8]) +
        AtA[2] * (AtA[3] * Atb[2] - Atb[1] * AtA[6])));

    out[2] = static_cast<float>(invDet * (
        AtA[0] * (AtA[4] * Atb[2] - Atb[1] * AtA[7]) +
        AtA[1] * (Atb[0] * AtA[7] - AtA[4] * AtA[6]) +
        Atb[0] * (AtA[3] * AtA[6] - AtA[3] * AtA[6])));

    LOGD("nativeSolvePivotCalibration: t_C^T = [%.1f, %.1f, %.1f] mm "
         "(N=%d, cond=%.1e).",
         out[0], out[1], out[2], numPoses, 1.0 / std::abs(det));

    env->ReleaseFloatArrayElements(rotationMatrices, R, JNI_ABORT);
    env->ReleaseFloatArrayElements(translationVectors, t, JNI_ABORT);
    env->ReleaseFloatArrayElements(outBracketOffset, out, 0);

    return 1;
}

/**
 * Returns the latest valid detection's 4×4 homogeneous pose matrix.
 * Call this after nativeDetectTags() to retrieve the camera-to-world transform.
 *
 * @param outPose  Float array of size 16 (row-major 4×4)
 * @return 1 if a valid pose is available, 0 otherwise
 */
JNIEXPORT jint JNICALL
Java_com_example_tracking_WeldVisionJNI_nativeGetLatestPose(
    JNIEnv *env,
    jobject /*thiz*/,
    jfloatArray outPose)
{
    jfloat *out = env->GetFloatArrayElements(outPose, nullptr);
    if (!out) return 0;

    {
        std::lock_guard<std::mutex> lock(g_detectorMutex);
        if (g_hasLatestPose) {
            std::memcpy(out, g_latestPose, 16 * sizeof(float));
            env->ReleaseFloatArrayElements(outPose, out, 0);
            return 1;
        }
    }

    env->ReleaseFloatArrayElements(outPose, out, JNI_ABORT);
    return 0;
}

} // extern "C"

extern "C" {
    JNIEXPORT void JNICALL
    Java_com_example_tracking_AprilTagTracker_initTracker(JNIEnv *env, jobject thiz) {
        __android_log_print(ANDROID_LOG_INFO, "AprilTagJNI", "Tracker initialized");
    }

    JNIEXPORT jfloatArray JNICALL
    Java_com_example_tracking_AprilTagTracker_processFrame(JNIEnv *env, jobject thiz,
                                                           jint width, jint height,
                                                           jbyteArray data) {
        // Stub for processing frame
        return nullptr;
    }
}
