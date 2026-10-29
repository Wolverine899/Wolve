package com.wolve.app.analytics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SleepScoreParts(val total: Int, val duration: Int, val efficiency: Int, val disturbance: Int)

/** Daily sleep score 0..100: duration 40, efficiency 35, disturbances 25. */
object SleepScore {
    fun of(n: SleepNight): SleepScoreParts {
        val d = 40.0 * min(1.0, n.totalMin.toDouble() / SleepEngine.NEED_MIN)
        val e = 35.0 * ((n.efficiency - 0.65) / 0.30).coerceIn(0.0, 1.0)
        val penalty = max(0, n.awakenings - 1) * 4.0 + max(0, n.awakeMin - 15) / 3.0 + max(0, n.latencyMin - 15) / 3.0
        val x = (25.0 - penalty).coerceIn(0.0, 25.0)
        return SleepScoreParts((d + e + x).roundToInt().coerceIn(0, 100), d.roundToInt(), e.roundToInt(), x.roundToInt())
    }

    fun label(score: Int): String = when {
        score >= 85 -> "Optimal"
        score >= 70 -> "Good"
        score >= 55 -> "Fair"
        else -> "Low"
    }
}
