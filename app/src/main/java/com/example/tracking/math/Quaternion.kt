package com.example.tracking.math

import kotlin.math.*

/**
 * Pure-Kotlin quaternion utility for spatial rotations.
 *
 * Quaternion representation: q = w + xi + yj + zk
 * Stored as [w, x, y, z].
 *
 * Used by SensorFusionFilter for IMU integration and camera pose correction.
 * All methods return new instances (immutable).
 */
data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {

    companion object {
        /** Identity quaternion (no rotation). */
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        /**
         * Creates a quaternion from an axis-angle rotation.
         * @param axis  Unit vector [ax, ay, az]
         * @param angle Rotation angle in radians
         */
        fun fromAxisAngle(axis: FloatArray, angle: Float): Quaternion {
            require(axis.size == 3) { "Axis must be 3-element array" }
            val halfAngle = angle / 2f
            val s = sin(halfAngle)
            val norm = sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2])
            if (norm < 1e-8f) return IDENTITY
            return Quaternion(cos(halfAngle), axis[0] / norm * s, axis[1] / norm * s, axis[2] / norm * s)
        }

        /**
         * Creates a quaternion from angular velocity integrated over dt.
         * q_gyro(dt) = [cos(|ω|·dt/2), (ω/|ω|)·sin(|ω|·dt/2)]
         *
         * @param omega Angular velocity vector [ωx, ωy, ωz] in rad/s
         * @param dt    Time delta in seconds
         */
        fun fromGyroscope(omega: FloatArray, dt: Float): Quaternion {
            require(omega.size == 3) { "Omega must be 3-element array" }
            val omegaMag = sqrt(omega[0] * omega[0] + omega[1] * omega[1] + omega[2] * omega[2])
            if (omegaMag < 1e-5f || dt <= 0f) return IDENTITY
            val theta = omegaMag * dt
            val sinHalf = sin(theta / 2f)
            return Quaternion(
                cos(theta / 2f),
                (omega[0] / omegaMag) * sinHalf,
                (omega[1] / omegaMag) * sinHalf,
                (omega[2] / omegaMag) * sinHalf
            )
        }

        /**
         * Creates a quaternion from a 3×3 rotation matrix (row-major).
         * Uses Shepperd's method for numerical stability.
         */
        fun fromRotationMatrix(r: FloatArray): Quaternion {
            require(r.size == 9) { "Rotation matrix must be 9 elements (3×3 row-major)" }
            val trace = r[0] + r[4] + r[8]
            return if (trace > 0f) {
                val s = sqrt(trace + 1f) * 2f
                Quaternion(s / 4f, (r[7] - r[5]) / s, (r[2] - r[6]) / s, (r[3] - r[1]) / s)
            } else if (r[0] > r[4] && r[0] > r[8]) {
                val s = sqrt(1f + r[0] - r[4] - r[8]) * 2f
                Quaternion((r[7] - r[5]) / s, s / 4f, (r[1] + r[3]) / s, (r[2] + r[6]) / s)
            } else if (r[4] > r[8]) {
                val s = sqrt(1f + r[4] - r[0] - r[8]) * 2f
                Quaternion((r[2] - r[6]) / s, (r[1] + r[3]) / s, s / 4f, (r[5] + r[7]) / s)
            } else {
                val s = sqrt(1f + r[8] - r[0] - r[4]) * 2f
                Quaternion((r[3] - r[1]) / s, (r[2] + r[6]) / s, (r[5] + r[7]) / s, s / 4f)
            }
        }
    }

    /** Quaternion norm (magnitude). */
    fun norm(): Float = sqrt(w * w + x * x + y * y + z * z)

    /** Returns a normalized copy (unit quaternion). */
    fun normalize(): Quaternion {
        val n = norm()
        return if (n > 1e-8f) Quaternion(w / n, x / n, y / n, z / n) else IDENTITY
    }

    /** Quaternion multiplication (Hamilton product): this ⊗ other. */
    fun multiply(other: Quaternion): Quaternion = Quaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w
    )

    /** Conjugate: q* = [w, -x, -y, -z]. */
    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    /** Inverse: q⁻¹ = q* / ||q||². */
    fun inverse(): Quaternion {
        val n2 = w * w + x * x + y * y + z * z
        return if (n2 > 1e-8f) Quaternion(w / n2, -x / n2, -y / n2, -z / n2) else IDENTITY
    }

    /** Linear interpolation between this and target (fast, approximate). */
    fun lerp(target: Quaternion, t: Float): Quaternion = Quaternion(
        w + (target.w - w) * t,
        x + (target.x - x) * t,
        y + (target.y - y) * t,
        z + (target.z - z) * t
    ).normalize()

    /** Spherical linear interpolation (SLERP) — smooth constant-speed rotation. */
    fun slerp(target: Quaternion, t: Float): Quaternion {
        var dot = (w * target.w + x * target.x + y * target.y + z * target.z).coerceIn(-1f, 1f)
        // If nearly parallel, fall back to lerp
        if (abs(dot) > 0.9995f) return lerp(target, t).normalize()
        // Take shortest path
        if (dot < 0f) { dot = -dot; return slerp(Quaternion(-target.w, -target.x, -target.y, -target.z), t) }
        val theta0 = acos(dot)
        val sinTheta0 = sin(theta0)
        val s0 = sin((1f - t) * theta0) / sinTheta0
        val s1 = sin(t * theta0) / sinTheta0
        return Quaternion(
            s0 * w + s1 * target.w,
            s0 * x + s1 * target.x,
            s0 * y + s1 * target.y,
            s0 * z + s1 * target.z
        )
    }

    /** Converts this quaternion to a 3×3 rotation matrix (row-major, 9 floats). */
    fun toRotationMatrix(): FloatArray {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        return floatArrayOf(
            1f - 2f * (yy + zz), 2f * (xy - wz),     2f * (xz + wy),
            2f * (xy + wz),     1f - 2f * (xx + zz), 2f * (yz - wx),
            2f * (xz - wy),     2f * (yz + wx),     1f - 2f * (xx + yy)
        )
    }

    /** Extracts Euler angles (roll, pitch, yaw) in radians. */
    fun toEuler(): FloatArray {
        val sinPitch = 2f * (w * y - z * x)
        val pitch = if (abs(sinPitch) >= 1f) (PI / 2f).toFloat() * sign(sinPitch) else asin(sinPitch)
        val roll  = atan2(2f * (w * x + y * z), 1f - 2f * (x * x + y * y))
        val yaw   = atan2(2f * (w * z + x * y), 1f - 2f * (y * y + z * z))
        return floatArrayOf(roll, pitch, yaw)
    }

    override fun toString(): String =
        "Quaternion(w=%.4f, x=%.4f, y=%.4f, z=%.4f)".format(w, x, y, z)
}
