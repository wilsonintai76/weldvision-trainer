#include "workpiece_frame.h"
#include <cmath>

// ───────────────────────────────────────────────────────
// 3-Point Touchdown Method
// ───────────────────────────────────────────────────────

WorkpieceFrame buildFromTouchdown(
    const Vec3& pStart,
    const Vec3& pEnd,
    const Vec3& pSurface
) {
    WorkpieceFrame frame;
    frame.valid = false;

    // ── Seam direction (Y-axis) ──────────────────────
    Vec3 seam = pEnd - pStart;
    frame.seamLengthMm = seam.norm();
    if (frame.seamLengthMm < 1.0) return frame;   // Degenerate

    Vec3 yAxis = seam / frame.seamLengthMm;

    // ── Surface normal (Z-axis) via two in-plane vectors ──
    Vec3 v1 = yAxis;                      // Along seam
    Vec3 v2 = pSurface - pStart;          // Start → surface point

    // Remove component parallel to seam
    Vec3 v2Perp = v2 - v1 * v2.dot(v1);
    double perpNorm = v2Perp.norm();
    if (perpNorm < 0.5) return frame;          // Surface point too close to seam

    // X_temp = v1 × v2Perp (perpendicular to seam in base plane)
    Vec3 xTemp = v1.cross(v2Perp);
    xTemp = xTemp.normalized();

    // Z = X_temp × Y (true upward normal)
    Vec3 zAxis = xTemp.cross(yAxis);
    zAxis = zAxis.normalized();

    // Ensure Z points away from base plate
    if ((pSurface - pStart).dot(zAxis) < 0.0) {
        zAxis = -zAxis;
    }

    // ── Final X-axis (orthonormal right-handed frame) ──
    Vec3 xAxis = yAxis.cross(zAxis);
    xAxis = xAxis.normalized();

    // ── Build 4×4 world-to-workpiece transform ───────
    // Rotation part: basis vectors as rows (inverse rotation)
    // Translation: -R^T · origin
    frame.worldToWorkpiece = Mat44(
        xAxis.x,  xAxis.y,  xAxis.z, -xAxis.dot(pStart),
        yAxis.x,  yAxis.y,  yAxis.z, -yAxis.dot(pStart),
        zAxis.x,  zAxis.y,  zAxis.z, -zAxis.dot(pStart),
        0.0,      0.0,      0.0,      1.0
    );

    frame.valid = true;
    return frame;
}

// ───────────────────────────────────────────────────────
// Automatic from AprilTag Grid (CNC fixtures only)
// ───────────────────────────────────────────────────────

WorkpieceFrame buildFromTagGrid(
    const Pose& tagGridPose,
    const Vec3& seamOffsetInTag,
    double seamLengthMm
) {
    WorkpieceFrame frame;
    frame.valid = false;

    Mat44 tagToWorld = tagGridPose.toMatrix();

    // Transform seam origin into world frame using the CAD-measured offset
    double ox, oy, oz, ow;
    tagToWorld.mulVec4(
        seamOffsetInTag.x, seamOffsetInTag.y, seamOffsetInTag.z, 1.0,
        ox, oy, oz, ow
    );
    Vec3 origin(ox, oy, oz);

    // ── Extract axes from tag pose ───────────────────
    Vec3 xAxis(tagToWorld(0,0), tagToWorld(1,0), tagToWorld(2,0));
    Vec3 yAxis(tagToWorld(0,1), tagToWorld(1,1), tagToWorld(2,1));
    Vec3 zAxis(tagToWorld(0,2), tagToWorld(1,2), tagToWorld(2,2));

    // ── Build transform ──────────────────────────────
    frame.worldToWorkpiece = Mat44(
        xAxis.x,  xAxis.y,  xAxis.z, -xAxis.dot(origin),
        yAxis.x,  yAxis.y,  yAxis.z, -yAxis.dot(origin),
        zAxis.x,  zAxis.y,  zAxis.z, -zAxis.dot(origin),
        0.0,      0.0,      0.0,      1.0
    );

    frame.seamLengthMm = seamLengthMm;
    frame.valid = true;
    return frame;
}
