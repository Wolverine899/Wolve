package com.wolve.app.analytics

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Stress on a 0..3 scale: HRV lower than your own daytime baseline means more stress. */
object StressMath {
    fun score(rmssd: Double, lnMean: Double, lnVar: Double): Double {
        val sd = max(sqrt(lnVar), 0.2)
        val z = ((lnMean - ln(max(1.0, rmssd))) / sd).coerceIn(-1.5, 1.5)
        return (1.5 + z).coerceIn(0.0, 3.0)
    }

    fun label(score: Double): String = when {
        score < 1.0 -> "Low"
        score < 2.0 -> "Moderate"
        else -> "High"
    }
}
