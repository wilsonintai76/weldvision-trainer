package com.example.data

/**
 * Pre-defined weave training challenges of increasing difficulty.
 */
data class WeaveTarget(
    val id: String,
    val name: String,
    val description: String,
    val difficulty: WeaveDifficulty,
    val targetWidthDeg: Float,       // degrees of travel-angle swing
    val targetFrequency: Float,      // Hz
    val targetSymmetry: Float,       // 0-1
    val minQualityScore: Float       // 0-100 to pass
)

enum class WeaveDifficulty(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced")
}

object WeaveTargets {
    val beginner = WeaveTarget(
        id = "weave_beginner_01",
        name = "Basic Zig-Zag",
        description = "Practice a consistent zig-zag weave with moderate width. Keep rhythm steady.",
        difficulty = WeaveDifficulty.BEGINNER,
        targetWidthDeg = 5f,
        targetFrequency = 1.8f,
        targetSymmetry = 0.75f,
        minQualityScore = 55f
    )

    val intermediate = WeaveTarget(
        id = "weave_intermediate_01",
        name = "Crescent Pattern",
        description = "Master the crescent weave — dwell briefly at each edge for better fusion.",
        difficulty = WeaveDifficulty.INTERMEDIATE,
        targetWidthDeg = 7f,
        targetFrequency = 2.2f,
        targetSymmetry = 0.88f,
        minQualityScore = 70f
    )

    val advanced = WeaveTarget(
        id = "weave_advanced_01",
        name = "Figure-Eight Weave",
        description = "Complex figure-eight pattern for maximum sidewall fusion control.",
        difficulty = WeaveDifficulty.ADVANCED,
        targetWidthDeg = 9f,
        targetFrequency = 1.6f,
        targetSymmetry = 0.93f,
        minQualityScore = 82f
    )

    fun all(): List<WeaveTarget> = listOf(beginner, intermediate, advanced)
    fun byId(id: String): WeaveTarget? = all().firstOrNull { it.id == id }
}
