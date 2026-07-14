package com.example.tracking

import kotlin.math.*

/**
 * Real-time weld bead physics engine. Simulates heat diffusion, puddle flow,
 * material deposition, and solidification on the WexelGrid.
 *
 * Called every simulation tick (~8 Hz) from the practice run loop.
 */
class BeadPhysics(private val grid: WexelGrid) {

    // ── Tunable parameters ──
    var heatIntensity = 0.35f       // Base heat per tick at torch center
    var heatRadius = 4              // Cells of heat-affected zone radius
    var coolingRate = 0.04f         // Heat lost per tick
    var depositionRate = 0.03f      // Puddle → solid conversion per tick
    var puddleSpreadRate = 0.08f    // How fast puddle flows to neighbors

    // ── Working buffers ──
    private val tempHeat = Array(grid.width) { FloatArray(grid.height) }

    /**
     * Advance the simulation one tick.
     * @param worldX     Torch world X position (mm) — along the joint
     * @param worldZ     Torch world Z position (mm) — across the joint
     * @param workAngle  Torch work angle (degrees) — wider angle = wider bead
     * @param speed      Travel speed (mm/s) — faster = less heat per cell
     */
    fun tick(worldX: Float, worldZ: Float, workAngle: Float, speed: Float) {
        applyHeat(worldX, worldZ, workAngle, speed)
        diffuseHeat()
        coolDown()
        flowPuddle()
        solidify()
    }

    // ── Heat application ──

    private fun applyHeat(wx: Float, wz: Float, workAngle: Float, speed: Float) {
        val (cx, cz) = grid.worldToGrid(wx, wz) ?: return

        val speedFactor = (1f - (speed / 20f).coerceIn(0f, 0.7f))
        val angleFactor = 1f + (abs(workAngle - 90f) / 45f).coerceIn(0f, 0.5f)
        val intensity = heatIntensity * speedFactor * angleFactor
        val radius = (heatRadius + (abs(workAngle - 90f) / 15f).toInt()).coerceIn(2, 12)

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val nx = cx + dx; val nz = cz + dz
                val cell = grid.getWexel(nx, nz) ?: continue
                val dist = sqrt((dx * dx + dz * dz).toFloat())
                val falloff = exp(-dist * dist / (radius * radius * 0.7f))
                val heat = intensity * falloff

                cell.heat = (cell.heat + heat).coerceIn(0f, 1f)
                cell.puddle = (cell.puddle + heat * 0.6f).coerceIn(0f, 1f)
            }
        }
    }

    // ── Heat diffusion (Gaussian blur) ──

    private fun diffuseHeat() {
        for (x in 0 until grid.width) for (z in 0 until grid.height) {
            tempHeat[x][z] = grid.getWexel(x, z)?.heat ?: 0f
        }
        for (x in 1 until grid.width - 1) {
            for (z in 1 until grid.height - 1) {
                val cell = grid.getWexel(x, z) ?: continue
                var sum = 0f
                var w = 0f
                for (dx in -1..1) for (dz in -1..1) {
                    val weight = if (dx == 0 && dz == 0) 0.25f else 0.09375f
                    sum += weight * tempHeat[x + dx][z + dz]
                    w += weight
                }
                cell.heat = (cell.heat * 0.4f + (sum / w) * 0.6f).coerceIn(0f, 1f)
            }
        }
    }

    // ── Cooling ──

    private fun coolDown() {
        for (x in 0 until grid.width) for (z in 0 until grid.height) {
            val cell = grid.getWexel(x, z) ?: continue
            cell.heat = (cell.heat - coolingRate).coerceIn(0f, 1f)
        }
    }

    // ── Puddle flow (gravity-driven downhill) ──

    private fun flowPuddle() {
        for (x in 0 until grid.width) for (z in 0 until grid.height) {
            val cell = grid.getWexel(x, z) ?: continue
            if (cell.puddle < 0.005f) continue

            val myHeight = cell.displacement + cell.puddle
            var lowestDz = 0; var lowestDx = 0; var lowestH = myHeight

            for (dx in -1..1) for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val nb = grid.getWexel(x + dx, z + dz) ?: continue
                val nbH = nb.displacement + nb.puddle
                if (nbH < lowestH) { lowestH = nbH; lowestDx = dx; lowestDz = dz }
            }

            if (lowestH < myHeight - 0.002f) {
                val flow = (cell.puddle * puddleSpreadRate).coerceAtMost(myHeight - lowestH)
                cell.puddle -= flow
                grid.getWexel(x + lowestDx, z + lowestDz)?.let {
                    it.puddle = (it.puddle + flow).coerceIn(0f, 1f)
                }
            }
        }
    }

    // ── Solidification ──

    private fun solidify() {
        val now = System.currentTimeMillis() / 1000f
        for (x in 0 until grid.width) for (z in 0 until grid.height) {
            val cell = grid.getWexel(x, z) ?: continue
            if (cell.heat < 0.15f && cell.puddle > 0.002f) {
                cell.displacement = (cell.displacement + cell.puddle * depositionRate).coerceIn(0f, 1f)
                cell.puddle *= 0.7f
                if (cell.puddle < 0.005f) {
                    cell.solidificationTime = now
                    cell.puddle = 0f
                }
            }
        }
    }
}
