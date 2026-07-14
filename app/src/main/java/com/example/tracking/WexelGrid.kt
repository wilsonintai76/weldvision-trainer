package com.example.tracking

import kotlin.math.*

/**
 * Wexel (Weld Pixel) — represents one cell on the weld plate surface.
 */
data class Wexel(
    var puddle: Float = 0f,            // Temporary liquid metal (0-1)
    var heat: Float = 0f,              // Temperature (0-1)
    var displacement: Float = 0f,      // Permanent solid bead height (0-1)
    var solidificationTime: Float = 0f // Timestamp when last solidified
)

/**
 * 2D grid representing the weld plate surface. Each cell is ~0.5mm.
 * The grid maps to a ~400mm × 100mm physical area.
 */
class WexelGrid(
    val width: Int = 200,           // Cells across (along joint)
    val height: Int = 80,           // Cells across joint width
    val cellSizeMm: Float = 0.5f    // mm per cell → 100mm × 40mm plate
) {
    private val grid = Array(width) { Array(height) { Wexel() } }

    val physicalWidthMm: Float = width * cellSizeMm   // ~100mm
    val physicalHeightMm: Float = height * cellSizeMm  // ~40mm

    /**
     * Convert world coordinates (mm) to grid indices.
     * originX, originZ: the world position of grid cell (0,0).
     */
    fun worldToGrid(worldX: Float, worldZ: Float, originX: Float = 0f, originZ: Float = -physicalHeightMm / 2f): Pair<Int, Int>? {
        val gx = ((worldX - originX) / cellSizeMm).toInt()
        val gz = ((worldZ - originZ) / cellSizeMm).toInt()
        return if (gx in 0 until width && gz in 0 until height) Pair(gx, gz) else null
    }

    fun getWexel(gx: Int, gz: Int): Wexel? =
        if (gx in 0 until width && gz in 0 until height) grid[gx][gz] else null

    fun heightAt(gx: Int, gz: Int): Float =
        getWexel(gx, gz)?.displacement ?: 0f

    fun getHeightMap(): FloatArray {
        return FloatArray(width * height) { i ->
            val x = i % width; val z = i / width
            grid[x][z].displacement
        }
    }

    fun getHeatMap(): FloatArray {
        return FloatArray(width * height) { i ->
            val x = i % width; val z = i / width
            grid[x][z].heat
        }
    }

    fun reset() {
        for (x in 0 until width) for (z in 0 until height) {
            grid[x][z] = Wexel()
        }
    }
}

data class GridRect(val x: Int, val z: Int, val w: Int, val h: Int) {
    val isEmpty get() = w <= 0 || h <= 0
}
