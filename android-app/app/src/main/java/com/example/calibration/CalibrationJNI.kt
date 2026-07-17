package com.example.calibration

object CalibrationJNI {
    init {
        System.loadLibrary("calibration_bridge")
    }

    // Phase 2: TCP Pivot
    external fun solveTcpPivot(
        poseMatrices: Array<Array<DoubleArray>>
    ): TcpProfile

    external fun getPivotCoverage(
        poseMatrices: Array<Array<DoubleArray>>
    ): Double

    // Phase 3: Workpiece Frame — Touchdown (printed stickers)
    external fun buildWorkpieceFrameFromTouchdown(
        pStartX: Double, pStartY: Double, pStartZ: Double,
        pEndX: Double,   pEndY: Double,   pEndZ: Double,
        pSurfX: Double,  pSurfY: Double,  pSurfZ: Double
    ): WorkpieceProfile

    // Phase 3: Workpiece Frame — Auto (CNC engraved tags only)
    external fun buildWorkpieceFrameFromTagGrid(
        tagGridPoseMatrix: Array<DoubleArray>,  // 4×4
        offsetX: Double, offsetY: Double, offsetZ: Double,
        seamLengthMm: Double
    ): WorkpieceProfile

    // Utility
    external fun transformToWorkpiece(
        worldX: Double, worldY: Double, worldZ: Double,
        frameMatrix: DoubleArray
    ): DoubleArray
}
