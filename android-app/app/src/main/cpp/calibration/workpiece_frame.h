#ifndef WORKPIECE_FRAME_H
#define WORKPIECE_FRAME_H

#include "../geometry/geometry.h"

// ───────────────────────────────────────────────────────
// Build workpiece frame from 3-point physical touchdown.
// Use for: 3D-printed fixtures with paper stickers where
// the tag grid may be slightly misaligned.
// ───────────────────────────────────────────────────────
// pStart  : tip placed at the START of the weld root crease
// pEnd    : tip placed at the END of the weld root crease
// pSurface: tip placed on the flat base plate (not on crease)
WorkpieceFrame buildFromTouchdown(
    const Vec3& pStart,
    const Vec3& pEnd,
    const Vec3& pSurface
);

// ───────────────────────────────────────────────────────
// Build workpiece frame automatically from AprilTag grid.
// ONLY for CNC-machined fixtures with engraved/etched tags
// where tag placement is accurate to ±0.05 mm.
// Do NOT use with 3D-printed + sticker fixtures.
// ───────────────────────────────────────────────────────
// tagGridPose       : pose of the AprilTag grid in camera frame
// seamOffsetInTag   : pre-measured vector from tag origin to
//                     weld seam start (from fixture CAD)
// seamLengthMm      : known seam length from fixture design
WorkpieceFrame buildFromTagGrid(
    const Pose& tagGridPose,
    const Vec3& seamOffsetInTag,
    double seamLengthMm
);

#endif // WORKPIECE_FRAME_H
