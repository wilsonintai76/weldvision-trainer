package com.example.calibration

import android.content.Context

/**
 * High-level orchestrator for the three-phase calibration workflow.
 *
 * Usage:
 *   val wizard = CalibrationWizard(context, bracketId, studentId)
 *   when (wizard.currentState()) {
 *       READY -> enterSimulation()
 *       else  -> launchCalibrationUI(wizard.currentState())
 *   }
 */
class CalibrationWizard(
    private val context: Context,
    private val bracketId: String,
    private val studentId: String
) {
    private val store = ProfilePersistenceStore(context)

    enum class State {
        UNCALIBRATED,
        CAMERA_NEEDED,
        TCP_NEEDED,
        WORKPIECE_NEEDED,
        READY
    }

    // ── Determine what needs calibration ─────────────

    fun currentState(): State {
        val profile = store.load(bracketId, studentId)

        if (profile == null) return State.UNCALIBRATED
        if (profile.camera == null) return State.CAMERA_NEEDED
        if (profile.tcp == null || !profile.tcp.quality.isValid())
            return State.TCP_NEEDED
        if (profile.workpiece == null || !profile.workpiece.valid)
            return State.WORKPIECE_NEEDED

        return State.READY
    }

    // ── Phase 2: Run pivot and store ─────────────────

    fun calibrateTcp(poses: Array<Array<DoubleArray>>): TcpProfile {
        val result = CalibrationJNI.solveTcpPivot(poses)

        val existing = store.load(bracketId, studentId)
        store.save(
            bracketId = bracketId,
            studentId = studentId,
            camera = existing?.camera,
            tcp = result,
            workpiece = existing?.workpiece
        )
        return result
    }

    // ── Phase 3a: Touchdown (printed sticker fixtures) ──

    fun calibrateWorkpieceTouchdown(
        pStart: Triple<Double, Double, Double>,
        pEnd: Triple<Double, Double, Double>,
        pSurface: Triple<Double, Double, Double>
    ): WorkpieceProfile {
        val result = CalibrationJNI.buildWorkpieceFrameFromTouchdown(
            pStart.first,  pStart.second,  pStart.third,
            pEnd.first,    pEnd.second,    pEnd.third,
            pSurface.first, pSurface.second, pSurface.third
        )

        val existing = store.load(bracketId, studentId)
        store.save(
            bracketId = bracketId,
            studentId = studentId,
            camera = existing?.camera,
            tcp = existing?.tcp,
            workpiece = result
        )
        return result
    }

    // ── Phase 3b: Auto from tag grid (CNC fixtures only) ──

    fun calibrateWorkpieceAuto(
        tagGridPose: Array<DoubleArray>,
        seamOffsetInTag: Triple<Double, Double, Double>,
        seamLengthMm: Double
    ): WorkpieceProfile {
        val result = CalibrationJNI.buildWorkpieceFrameFromTagGrid(
            tagGridPose,
            seamOffsetInTag.first,
            seamOffsetInTag.second,
            seamOffsetInTag.third,
            seamLengthMm
        )

        val existing = store.load(bracketId, studentId)
        store.save(
            bracketId = bracketId,
            studentId = studentId,
            camera = existing?.camera,
            tcp = existing?.tcp,
            workpiece = result
        )
        return result
    }

    // ── Convenience: auto-select touchdown vs auto ────

    fun calibrateWorkpiece(
        fixtureType: FixtureType,
        pStart: Triple<Double, Double, Double>? = null,
        pEnd: Triple<Double, Double, Double>? = null,
        pSurface: Triple<Double, Double, Double>? = null,
        tagGridPose: Array<DoubleArray>? = null,
        seamOffsetInTag: Triple<Double, Double, Double>? = null,
        seamLengthMm: Double? = null
    ): WorkpieceProfile {
        return when (fixtureType) {
            FixtureType.PRINTED_STICKER -> {
                requireNotNull(pStart) { "Touchdown requires pStart" }
                requireNotNull(pEnd) { "Touchdown requires pEnd" }
                requireNotNull(pSurface) { "Touchdown requires pSurface" }
                calibrateWorkpieceTouchdown(pStart, pEnd, pSurface)
            }
            FixtureType.CNC_ENGRAVED -> {
                requireNotNull(tagGridPose) { "Auto-frame requires tagGridPose" }
                requireNotNull(seamOffsetInTag) { "Auto-frame requires seamOffsetInTag" }
                requireNotNull(seamLengthMm) { "Auto-frame requires seamLengthMm" }
                calibrateWorkpieceAuto(tagGridPose, seamOffsetInTag, seamLengthMm)
            }
        }
    }

    // ── Live coverage during pivot ───────────────────

    fun liveCoverage(poses: Array<Array<DoubleArray>>): Double {
        return CalibrationJNI.getPivotCoverage(poses)
    }

    // ── Load the active profile ──────────────────────

    fun loadProfile(): CalibrationProfile? {
        return store.load(bracketId, studentId)
    }

    // ── Reset this student's calibration ─────────────

    fun reset() {
        store.delete(bracketId, studentId)
    }

    // ── Reset everything (instructor tool) ───────────

    fun resetAll() {
        store.clearAll()
    }
}
