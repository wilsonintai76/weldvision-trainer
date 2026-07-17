#include <jni.h>
#include <android/log.h>
#include "../geometry/geometry.h"
#include "../calibration/tcp_solver.h"
#include "../calibration/workpiece_frame.h"

#define TAG "WeldCalib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ───────────────────────────────────────────────────────
// Helper: unpack Java double[][][] → std::vector<Pose>
// ───────────────────────────────────────────────────────

static std::vector<Pose> javaToPoses(JNIEnv* env, jobjectArray poseArray) {
    std::vector<Pose> poses;
    const jsize n = env->GetArrayLength(poseArray);
    poses.reserve(static_cast<size_t>(n));

    for (jsize i = 0; i < n; ++i) {
        auto row4 = static_cast<jobjectArray>(
            env->GetObjectArrayElement(poseArray, i)
        );
        Mat44 m;
        for (int r = 0; r < 4; ++r) {
            auto row = static_cast<jdoubleArray>(
                env->GetObjectArrayElement(row4, r)
            );
            jdouble* d = env->GetDoubleArrayElements(row, nullptr);
            for (int c = 0; c < 4; ++c) {
                m(r, c) = d[c];
            }
            env->ReleaseDoubleArrayElements(row, d, JNI_ABORT);
            env->DeleteLocalRef(row);
        }
        env->DeleteLocalRef(row4);
        poses.push_back(Pose::fromMatrix(m));
    }
    return poses;
}

// ───────────────────────────────────────────────────────
// Helper: unpack Java double[][] (4×4 matrix)
// ───────────────────────────────────────────────────────

static Mat44 javaToMat44(JNIEnv* env, jobjectArray matrixArray) {
    Mat44 m;
    for (int r = 0; r < 4; ++r) {
        auto row = static_cast<jdoubleArray>(
            env->GetObjectArrayElement(matrixArray, r)
        );
        jdouble* d = env->GetDoubleArrayElements(row, nullptr);
        for (int c = 0; c < 4; ++c) {
            m(r, c) = d[c];
        }
        env->ReleaseDoubleArrayElements(row, d, JNI_ABORT);
        env->DeleteLocalRef(row);
    }
    return m;
}

// ───────────────────────────────────────────────────────
// JNI: solveTcpPivot
// Kotlin: CalibrationJNI.solveTcpPivot(Array<Array<DoubleArray>>): TcpProfile
// ───────────────────────────────────────────────────────

extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_calibration_CalibrationJNI_solveTcpPivot(
    JNIEnv* env,
    jclass /* clazz */,
    jobjectArray poseMatrices
) {
    // 1. Unpack Java arrays → C++ Pose vector
    auto poses = javaToPoses(env, poseMatrices);

    // 2. Run pivot solver
    PivotTcpSolver solver;
    TcpResult result = solver.solve(poses);

    // 3. Find Java classes
    jclass qualityClass = env->FindClass(
        "com/example/calibration/TcpQualityProfile"
    );
    jmethodID qualityCtor = env->GetMethodID(
        qualityClass, "<init>", "(DDDDDI)V"
    );

    jclass tcpClass = env->FindClass(
        "com/example/calibration/TcpProfile"
    );
    jmethodID tcpCtor = env->GetMethodID(
        tcpClass, "<init>",
        "(DDDLcom/example/calibration/TcpQualityProfile;)V"
    );

    if (!qualityClass || !qualityCtor || !tcpClass || !tcpCtor) {
        LOGE("Failed to find Java calibration classes or constructors");
        return nullptr;
    }

    // 4. Create Java TcpQualityProfile
    jobject javaQuality = env->NewObject(
        qualityClass,
        qualityCtor,
        result.quality.residualMm,
        result.quality.coveragePercent,
        result.quality.conditionNumber,
        result.quality.reprojectionMeanMm,
        result.quality.reprojectionMaxMm,
        static_cast<jint>(result.quality.sampleCount)
    );

    // 5. Create Java TcpProfile
    jobject javaProfile = env->NewObject(
        tcpClass,
        tcpCtor,
        result.offset.x,
        result.offset.y,
        result.offset.z,
        javaQuality
    );

    // 6. Clean up local references
    env->DeleteLocalRef(qualityClass);
    env->DeleteLocalRef(tcpClass);
    env->DeleteLocalRef(javaQuality);

    return javaProfile;
}

// ───────────────────────────────────────────────────────
// JNI: getPivotCoverage (live UI feedback during pivot)
// Kotlin: CalibrationJNI.getPivotCoverage(Array<Array<DoubleArray>>): Double
// ───────────────────────────────────────────────────────

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_example_calibration_CalibrationJNI_getPivotCoverage(
    JNIEnv* env,
    jclass /* clazz */,
    jobjectArray poseMatrices
) {
    auto poses = javaToPoses(env, poseMatrices);
    PivotTcpSolver solver;
    return solver.computeCoverage(poses);
}

// ───────────────────────────────────────────────────────
// JNI: buildWorkpieceFrameFromTouchdown (3-point method)
// Kotlin: CalibrationJNI.buildWorkpieceFrameFromTouchdown(...): WorkpieceProfile
// ───────────────────────────────────────────────────────

extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_calibration_CalibrationJNI_buildWorkpieceFrameFromTouchdown(
    JNIEnv* env,
    jclass /* clazz */,
    jdouble sx, jdouble sy, jdouble sz,
    jdouble ex, jdouble ey, jdouble ez,
    jdouble px, jdouble py, jdouble pz
) {
    Vec3 pStart(sx, sy, sz);
    Vec3 pEnd(ex, ey, ez);
    Vec3 pSurface(px, py, pz);

    WorkpieceFrame frame = buildFromTouchdown(pStart, pEnd, pSurface);

    // Pack 4×4 matrix into double[16]
    jdoubleArray matArray = env->NewDoubleArray(16);
    jdouble* m = env->GetDoubleArrayElements(matArray, nullptr);
    for (int r = 0; r < 4; ++r) {
        for (int c = 0; c < 4; ++c) {
            m[r * 4 + c] = frame.worldToWorkpiece(r, c);
        }
    }
    env->ReleaseDoubleArrayElements(matArray, m, 0);

    // Create Java WorkpieceProfile
    jclass wpClass = env->FindClass(
        "com/example/calibration/WorkpieceProfile"
    );
    jmethodID wpCtor = env->GetMethodID(
        wpClass, "<init>", "([DZDLjava/lang/String;)V"
    );

    jstring method = env->NewStringUTF("touchdown");

    jobject javaProfile = env->NewObject(
        wpClass,
        wpCtor,
        matArray,
        static_cast<jboolean>(frame.valid),
        frame.seamLengthMm,
        method
    );

    env->DeleteLocalRef(matArray);
    env->DeleteLocalRef(method);
    env->DeleteLocalRef(wpClass);

    return javaProfile;
}

// ───────────────────────────────────────────────────────
// JNI: buildWorkpieceFrameFromTagGrid (CNC fixtures only)
// Kotlin: CalibrationJNI.buildWorkpieceFrameFromTagGrid(...): WorkpieceProfile
// ───────────────────────────────────────────────────────

extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_calibration_CalibrationJNI_buildWorkpieceFrameFromTagGrid(
    JNIEnv* env,
    jclass /* clazz */,
    jobjectArray tagGridPoseMatrix,
    jdouble offsetX, jdouble offsetY, jdouble offsetZ,
    jdouble seamLengthMm
) {
    // Unpack tag grid pose
    Mat44 m = javaToMat44(env, tagGridPoseMatrix);
    Pose tagPose = Pose::fromMatrix(m);
    Vec3 offset(offsetX, offsetY, offsetZ);

    WorkpieceFrame frame = buildFromTagGrid(tagPose, offset, seamLengthMm);

    // Pack 4×4 matrix into double[16]
    jdoubleArray matArray = env->NewDoubleArray(16);
    jdouble* mat = env->GetDoubleArrayElements(matArray, nullptr);
    for (int r = 0; r < 4; ++r) {
        for (int c = 0; c < 4; ++c) {
            mat[r * 4 + c] = frame.worldToWorkpiece(r, c);
        }
    }
    env->ReleaseDoubleArrayElements(matArray, mat, 0);

    // Create Java WorkpieceProfile
    jclass wpClass = env->FindClass(
        "com/example/calibration/WorkpieceProfile"
    );
    jmethodID wpCtor = env->GetMethodID(
        wpClass, "<init>", "([DZDLjava/lang/String;)V"
    );

    jstring method = env->NewStringUTF("tag_auto");

    jobject javaProfile = env->NewObject(
        wpClass,
        wpCtor,
        matArray,
        static_cast<jboolean>(frame.valid),
        frame.seamLengthMm,
        method
    );

    env->DeleteLocalRef(matArray);
    env->DeleteLocalRef(method);
    env->DeleteLocalRef(wpClass);

    return javaProfile;
}

// ───────────────────────────────────────────────────────
// JNI: transformToWorkpiece
// Kotlin: CalibrationJNI.transformToWorkpiece(...): DoubleArray
// ───────────────────────────────────────────────────────

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_example_calibration_CalibrationJNI_transformToWorkpiece(
    JNIEnv* env,
    jclass /* clazz */,
    jdouble wx, jdouble wy, jdouble wz,
    jdoubleArray frameMatrix
) {
    // Unpack 4×4 matrix (row-major double[16])
    jdouble* fm = env->GetDoubleArrayElements(frameMatrix, nullptr);
    Mat44 mat;
    for (int r = 0; r < 4; ++r) {
        for (int c = 0; c < 4; ++c) {
            mat(r, c) = fm[r * 4 + c];
        }
    }
    env->ReleaseDoubleArrayElements(frameMatrix, fm, JNI_ABORT);

    // Transform
    WorkpieceFrame frame;
    frame.worldToWorkpiece = mat;
    frame.valid = true;
    Vec3 result = frame.transform(Vec3(wx, wy, wz));

    // Return double[3]
    jdoubleArray out = env->NewDoubleArray(3);
    jdouble* o = env->GetDoubleArrayElements(out, nullptr);
    o[0] = result.x;
    o[1] = result.y;
    o[2] = result.z;
    env->ReleaseDoubleArrayElements(out, o, 0);

    return out;
}
