package com.example.calibration

import org.json.JSONArray
import org.json.JSONObject

/**
 * Calibration profile — the top-level container for all calibration data.
 * Serialized to/from JSON via org.json (built into Android, no extra deps).
 */
data class CalibrationProfile(
    val version: Int = 2,
    val deviceModel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val camera: CameraProfile? = null,
    val tcp: TcpProfile? = null,
    val workpiece: WorkpieceProfile? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("deviceModel", deviceModel)
        put("createdAt", createdAt)
        camera?.let { put("camera", it.toJson()) }
        tcp?.let { put("tcp", it.toJson()) }
        workpiece?.let { put("workpiece", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JSONObject): CalibrationProfile {
            return CalibrationProfile(
                version = json.optInt("version", 2),
                deviceModel = json.optString("deviceModel", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                camera = if (json.has("camera")) CameraProfile.fromJson(json.getJSONObject("camera")) else null,
                tcp = if (json.has("tcp")) TcpProfile.fromJson(json.getJSONObject("tcp")) else null,
                workpiece = if (json.has("workpiece")) WorkpieceProfile.fromJson(json.getJSONObject("workpiece")) else null
            )
        }
    }
}

data class CameraProfile(
    val matrix: List<Double>,       // 3×3 row-major, 9 elements
    val distortion: List<Double>,   // 5 elements (k1, k2, p1, p2, k3)
    val focusMode: String = "INFINITY",
    val calibratedAtDistanceMm: Float = 250f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("matrix", JSONArray(matrix))
        put("distortion", JSONArray(distortion))
        put("focusMode", focusMode)
        put("calibratedAtDistanceMm", calibratedAtDistanceMm.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): CameraProfile {
            val matrixArr = json.getJSONArray("matrix")
            val distArr = json.getJSONArray("distortion")
            return CameraProfile(
                matrix = (0 until matrixArr.length()).map { matrixArr.getDouble(it) },
                distortion = (0 until distArr.length()).map { distArr.getDouble(it) },
                focusMode = json.optString("focusMode", "INFINITY"),
                calibratedAtDistanceMm = json.optDouble("calibratedAtDistanceMm", 250.0).toFloat()
            )
        }
    }
}

data class TcpProfile(
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val quality: TcpQualityProfile
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("offsetX", offsetX)
        put("offsetY", offsetY)
        put("offsetZ", offsetZ)
        put("quality", quality.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): TcpProfile {
            return TcpProfile(
                offsetX = json.getDouble("offsetX"),
                offsetY = json.getDouble("offsetY"),
                offsetZ = json.getDouble("offsetZ"),
                quality = TcpQualityProfile.fromJson(json.getJSONObject("quality"))
            )
        }
    }
}

data class TcpQualityProfile(
    val residualMm: Double,
    val coveragePercent: Double,
    val conditionNumber: Double,
    val reprojectionMeanMm: Double,
    val reprojectionMaxMm: Double,
    val sampleCount: Int
) {
    /**
     * IMPORTANT: conditionNumber is the only metric that guards against
     * degenerate pivot motion (e.g. roll-only rotation).
     * reprojectionMeanMm measures sample consistency, not absolute accuracy,
     * and cannot detect a systematically wrong offset in the null space.
     * Do not remove the conditionNumber gate.
     */
    fun isValid(): Boolean =
        sampleCount >= 5 &&
        coveragePercent >= 70.0 &&
        residualMm <= 0.5 &&
        conditionNumber <= 100.0 &&
        reprojectionMeanMm <= 0.3

    fun toJson(): JSONObject = JSONObject().apply {
        put("residualMm", residualMm)
        put("coveragePercent", coveragePercent)
        put("conditionNumber", conditionNumber)
        put("reprojectionMeanMm", reprojectionMeanMm)
        put("reprojectionMaxMm", reprojectionMaxMm)
        put("sampleCount", sampleCount)
    }

    companion object {
        fun fromJson(json: JSONObject): TcpQualityProfile {
            return TcpQualityProfile(
                residualMm = json.getDouble("residualMm"),
                coveragePercent = json.getDouble("coveragePercent"),
                conditionNumber = json.getDouble("conditionNumber"),
                reprojectionMeanMm = json.getDouble("reprojectionMeanMm"),
                reprojectionMaxMm = json.getDouble("reprojectionMaxMm"),
                sampleCount = json.getInt("sampleCount")
            )
        }
    }
}

data class WorkpieceProfile(
    val matrix: List<Double>,     // 4×4 row-major, 16 elements
    val valid: Boolean,
    val seamLengthMm: Double,
    val method: String            // "touchdown" or "tag_auto"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("matrix", JSONArray(matrix))
        put("valid", valid)
        put("seamLengthMm", seamLengthMm)
        put("method", method)
    }

    companion object {
        fun fromJson(json: JSONObject): WorkpieceProfile {
            val matrixArr = json.getJSONArray("matrix")
            return WorkpieceProfile(
                matrix = (0 until matrixArr.length()).map { matrixArr.getDouble(it) },
                valid = json.getBoolean("valid"),
                seamLengthMm = json.getDouble("seamLengthMm"),
                method = json.getString("method")
            )
        }
    }
}

/**
 * Controls which workpiece calibration path is used.
 */
enum class FixtureType {
    /** 3D-printed + paper AprilTags → requires 3-point touchdown */
    PRINTED_STICKER,
    /** CNC-machined aluminium + engraved/etched tags → auto-frame safe */
    CNC_ENGRAVED
}
