package com.wolve.app.analytics

data class StrainPlan(val title: String, val low: Int, val high: Int, val text: String)

/** On-device coaching rules (no network, not medical advice). */
object Coach {
    fun strainTarget(recovery: Int?): StrainPlan = when {
        recovery == null -> StrainPlan("Track a night first", 8, 14, "Track a night of sleep and Wolve will tailor a daily strain target to your recovery.")
        recovery >= 67 -> StrainPlan("Push", 14, 18, "Your body is well recovered. A solid training day is a good use of it.")
        recovery >= 34 -> StrainPlan("Maintain", 10, 14, "Moderate recovery. Keep effort steady and avoid maxing out.")
        else -> StrainPlan("Recover", 4, 10, "Low recovery. Favour light movement, hydration and an earlier night.")
    }

    /** Minutes of sleep to aim for: a base, plus extra after hard days and recent sleep debt. */
    fun sleepNeedMin(yesterdayStrain: Double, recent: List<SleepNight>): Int {
        val strainAdj = ((yesterdayStrain - 8.0).coerceAtLeast(0.0) * 6.0).toInt()
        val debt = recent.takeLast(3).sumOf { (SleepEngine.NEED_MIN - it.totalMin).coerceAtLeast(0) } / 3
        return (450 + strainAdj + debt / 2).coerceIn(390, 630)
    }

    /** Suggested bedtime as minutes after midnight, given the wake time (minutes after midnight). */
    fun bedtimeMin(wakeMin: Int, needMin: Int, latencyMin: Int = 15): Int =
        (((wakeMin - needMin - latencyMin) % 1440) + 1440) % 1440
}
