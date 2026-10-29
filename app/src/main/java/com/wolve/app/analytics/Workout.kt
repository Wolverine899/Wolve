package com.wolve.app.analytics

data class Workout(
    val startTs: Long,
    val endTs: Long,
    val sport: String,
    val avgHr: Int,
    val maxHr: Int,
    val kcal: Int,
    val strain: Double,
    /** Seconds in zones 0..5 (0 = below 50% of max heart rate). */
    val zoneSec: List<Int>,
)

val SPORTS = listOf("Running", "Walking", "Cycling", "Strength", "HIIT", "Yoga", "Football", "Basketball", "Swimming", "Other")

object Zones {
    fun zoneOf(bpm: Int, maxHr: Double): Int {
        val p = bpm / maxHr
        return when {
            p < 0.5 -> 0
            p < 0.6 -> 1
            p < 0.7 -> 2
            p < 0.8 -> 3
            p < 0.9 -> 4
            else -> 5
        }
    }

    /** Keytel et al. heart-rate based energy expenditure, kcal per minute. */
    fun kcalPerMin(bpm: Int, age: Int, weightKg: Double, male: Boolean): Double {
        val kj = if (male) -55.0969 + 0.6309 * bpm + 0.1988 * weightKg + 0.2017 * age
        else -20.4022 + 0.4472 * bpm - 0.1263 * weightKg + 0.074 * age
        val k = kj / 4.184
        return if (k < 0.0) 0.0 else k
    }
}
