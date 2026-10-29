package com.wolve.app.analytics

import kotlin.math.exp

/** Day strain on a 0..21 scale from Banister's exponential TRIMP over heart-rate reserve. */
object StrainMath {
    /** User-set maximum heart rate; 0 means automatic (Tanaka). */
    @Volatile
    var maxHrOverride: Double = 0.0

    fun maxHr(age: Int): Double = if (maxHrOverride > 0.0) maxHrOverride else 208.0 - 0.7 * age

    fun trimp(dtMin: Double, hrr: Double, male: Boolean): Double {
        val a = if (male) 0.64 else 0.86
        val b = if (male) 1.92 else 1.67
        return dtMin * hrr * a * exp(b * hrr)
    }

    fun scale(load: Double): Double = 21.0 * (1.0 - exp(-load / 150.0))
}
