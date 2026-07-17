#ifndef WELDVISION_GEOMETRY_H
#define WELDVISION_GEOMETRY_H

#include <cmath>
#include <algorithm>

// ───────────────────────────────────────────────────────
// Vec3: 3-element vector, stack-allocated
// ───────────────────────────────────────────────────────

struct Vec3 {
    double x, y, z;

    Vec3() : x(0), y(0), z(0) {}
    Vec3(double x_, double y_, double z_) : x(x_), y(y_), z(z_) {}

    Vec3 operator+(const Vec3& o) const { return {x + o.x, y + o.y, z + o.z}; }
    Vec3 operator-(const Vec3& o) const { return {x - o.x, y - o.y, z - o.z}; }
    Vec3 operator*(double s) const { return {x * s, y * s, z * s}; }
    Vec3 operator/(double s) const { return {x / s, y / s, z / s}; }
    Vec3 operator-() const { return {-x, -y, -z}; }

    Vec3& operator+=(const Vec3& o) { x += o.x; y += o.y; z += o.z; return *this; }
    Vec3& operator-=(const Vec3& o) { x -= o.x; y -= o.y; z -= o.z; return *this; }

    double dot(const Vec3& o) const { return x * o.x + y * o.y + z * o.z; }
    Vec3 cross(const Vec3& o) const {
        return {y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x};
    }
    double norm() const { return std::sqrt(x * x + y * y + z * z); }
    Vec3 normalized() const {
        double n = norm();
        return (n > 1e-12) ? (*this / n) : Vec3{0, 0, 0};
    }

    double operator()(int i) const { return (&x)[i]; }
    double& operator()(int i) { return (&x)[i]; }
};

// ───────────────────────────────────────────────────────
// Mat33: 3×3 matrix, row-major, stack-allocated
// ───────────────────────────────────────────────────────

struct Mat33 {
    double m[9]; // row-major: m[row*3 + col]

    Mat33() {
        m[0]=1; m[1]=0; m[2]=0;
        m[3]=0; m[4]=1; m[5]=0;
        m[6]=0; m[7]=0; m[8]=1;
    }

    Mat33(double m00, double m01, double m02,
          double m10, double m11, double m12,
          double m20, double m21, double m22) {
        m[0]=m00; m[1]=m01; m[2]=m02;
        m[3]=m10; m[4]=m11; m[5]=m12;
        m[6]=m20; m[7]=m21; m[8]=m22;
    }

    double operator()(int r, int c) const { return m[r * 3 + c]; }
    double& operator()(int r, int c) { return m[r * 3 + c]; }

    Mat33 operator+(const Mat33& o) const {
        Mat33 r;
        for (int i = 0; i < 9; ++i) r.m[i] = m[i] + o.m[i];
        return r;
    }
    Mat33 operator-(const Mat33& o) const {
        Mat33 r;
        for (int i = 0; i < 9; ++i) r.m[i] = m[i] - o.m[i];
        return r;
    }
    Vec3 operator*(const Vec3& v) const {
        return {
            m[0]*v.x + m[1]*v.y + m[2]*v.z,
            m[3]*v.x + m[4]*v.y + m[5]*v.z,
            m[6]*v.x + m[7]*v.y + m[8]*v.z
        };
    }
};

// ───────────────────────────────────────────────────────
// Mat44: 4×4 homogeneous transform, row-major
// ───────────────────────────────────────────────────────

struct Mat44 {
    double m[16]; // row-major: m[row*4 + col]

    Mat44() {
        for (int i = 0; i < 16; ++i) m[i] = 0.0;
        m[0] = m[5] = m[10] = m[15] = 1.0;
    }

    Mat44(double m00, double m01, double m02, double m03,
          double m10, double m11, double m12, double m13,
          double m20, double m21, double m22, double m23,
          double m30, double m31, double m32, double m33) {
        m[0]=m00;  m[1]=m01;  m[2]=m02;  m[3]=m03;
        m[4]=m10;  m[5]=m11;  m[6]=m12;  m[7]=m13;
        m[8]=m20;  m[9]=m21;  m[10]=m22; m[11]=m23;
        m[12]=m30; m[13]=m31; m[14]=m32; m[15]=m33;
    }

    double operator()(int r, int c) const { return m[r * 4 + c]; }
    double& operator()(int r, int c) { return m[r * 4 + c]; }

    // Multiply 4×4 × 4×1 column vector (returns Vec4-like via Vec3 + w)
    void mulVec4(double vx, double vy, double vz, double vw,
                 double& rx, double& ry, double& rz, double& rw) const {
        rx = m[0]*vx + m[1]*vy + m[2]*vz  + m[3]*vw;
        ry = m[4]*vx + m[5]*vy + m[6]*vz  + m[7]*vw;
        rz = m[8]*vx + m[9]*vy + m[10]*vz + m[11]*vw;
        rw = m[12]*vx+ m[13]*vy+ m[14]*vz+ m[15]*vw;
    }
};

// ───────────────────────────────────────────────────────
// Pose: Rotation + Translation, stack-allocated
// ───────────────────────────────────────────────────────

struct Pose {
    Mat33 R;   // 3×3 rotation matrix
    Vec3  t;   // 3×1 translation vector (millimeters)

    // Factory: construct from a 4×4 homogeneous transform
    static Pose fromMatrix(const Mat44& m) {
        return {
            Mat33(m(0,0), m(0,1), m(0,2),
                  m(1,0), m(1,1), m(1,2),
                  m(2,0), m(2,1), m(2,2)),
            Vec3(m(0,3), m(1,3), m(2,3))
        };
    }

    // Convert to 4×4 homogeneous transform
    Mat44 toMatrix() const {
        return Mat44(
            R(0,0), R(0,1), R(0,2), t.x,
            R(1,0), R(1,1), R(1,2), t.y,
            R(2,0), R(2,1), R(2,2), t.z,
            0.0,    0.0,    0.0,    1.0
        );
    }
};

// ───────────────────────────────────────────────────────
// Quality metrics for TCP calibration
// ───────────────────────────────────────────────────────

struct TcpQuality {
    double residualMm          = 0.0;   // Least-squares residual
    double coveragePercent     = 0.0;   // Pivot cone angular spread
    double conditionNumber     = 0.0;   // Design matrix condition number
    double reprojectionMeanMm  = 0.0;   // Mean tip convergence error
    double reprojectionMaxMm   = 0.0;   // Worst tip convergence error
    int    sampleCount         = 0;     // Number of poses collected

    // Gate: is this calibration reliable enough for simulation?
    //
    // IMPORTANT — Metric responsibilities:
    //   coveragePercent      Catches: narrow pivot cone (pre-condition gate)
    //   conditionNumber      Catches: degenerate motion (e.g. roll-only rotation)
    //                          This is the ONLY metric that guards against a
    //                          systematically wrong TCP offset in the null space
    //                          of the pivot. Do not remove this gate.
    //   residualMm           Catches: measurement noise
    //   reprojectionMeanMm   Catches: inconsistent samples (tip slipped during pivot)
    //                          This measures spread BETWEEN samples. A uniform shift
    //                          in the null-space direction produces identical
    //                          reprojection error to the true offset and CANNOT
    //                          detect that class of error.
    bool isValid() const {
        return sampleCount >= 5
            && coveragePercent >= 70.0
            && residualMm <= 0.5
            && conditionNumber <= 100.0
            && reprojectionMeanMm <= 0.3;
    }
};

// ───────────────────────────────────────────────────────
// TCP result: the offset vector + diagnostics
// ───────────────────────────────────────────────────────

struct TcpResult {
    Vec3       offset;   // Vector from tag center to torch tip (mm), in tag frame
    TcpQuality quality;  // Diagnostic metrics
};

// ───────────────────────────────────────────────────────
// Workpiece coordinate frame
// ───────────────────────────────────────────────────────

struct WorkpieceFrame {
    Mat44  worldToWorkpiece;   // 4×4 transformation matrix
    double seamLengthMm = 0.0; // Length of weld seam
    bool   valid = false;      // Whether the frame is usable

    // Transform a point from world (camera) space into workpiece space.
    // Result: X = across seam, Y = along seam (travel distance), Z = height (CTWD).
    Vec3 transform(const Vec3& worldPoint) const {
        double rx, ry, rz, rw;
        worldToWorkpiece.mulVec4(worldPoint.x, worldPoint.y, worldPoint.z, 1.0,
                                  rx, ry, rz, rw);
        return Vec3(rx, ry, rz);
    }
};

// ───────────────────────────────────────────────────────
// Fixture type: controls which workpiece calibration
// method is appropriate
// ───────────────────────────────────────────────────────

enum class FixtureType {
    PRINTED_STICKER,   // 3D-printed + paper AprilTags → requires 3-point touchdown
    CNC_ENGRAVED       // Machined aluminium + etched tags → auto-frame from tag grid
};

#endif // WELDVISION_GEOMETRY_H
