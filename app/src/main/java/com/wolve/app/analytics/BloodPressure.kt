package com.wolve.app.analytics

import com.wolve.app.data.BpCalPoint
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

enum class BpCategory(val label: String) {
    NORMAL("Normal"),
    ELEVATED("Elevated"),
    STAGE1("High, stage 1"),
    STAGE2("High, stage 2"),
    CRISIS("Very high"),
}

data class BpEstimate(
    val sys: Double,
    val dia: Double,
    /** Plausible +/- error in mmHg (not a statistical guarantee). */
    val margin: Double,
    val category: BpCategory,
    val calibrated: Boolean,
)

/**
 * Cuffless blood-pressure ESTIMATE from heart rate and HRV (RMSSD), optionally derived from ECG R-R intervals.
 *
 * This is a population prior that is personalised with the user's own cuff readings (ridge regression
 * shrunk toward the prior). Without pulse-transit-time (needs ECG + PPG) the signal is weak, so the model
 * reports a wide margin and must never replace a validated cuff. It is not a medical device.
 */
object BloodPressure {
    private const val LAMBDA_HR = 60.0
    private const val LAMBDA_HRV = 2.0
    private val LN_REF = ln(40.0)

    private fun priorSys(hr: Double, lnR: Double) = 117.0 + 0.30 * (hr - 70.0) - 3.0 * (lnR - LN_REF)
    private fun priorDia(hr: Double, lnR: Double) = 75.0 + 0.20 * (hr - 70.0) - 2.0 * (lnR - LN_REF)

    fun categorize(sys: Double, dia: Double): BpCategory = when {
        sys > 180.0 || dia > 120.0 -> BpCategory.CRISIS
        sys >= 140.0 || dia >= 90.0 -> BpCategory.STAGE2
        sys >= 130.0 || dia >= 80.0 -> BpCategory.STAGE1
        sys >= 120.0 -> BpCategory.ELEVATED
        else -> BpCategory.NORMAL
    }

    fun estimate(hr: Double, rmssd: Double, cal: List<BpCalPoint>): BpEstimate {
        val lnR = ln(max(5.0, rmssd))
        val n = cal.size
        var sys: Double
        var dia: Double
        val margin: Double

        when {
            n == 0 -> {
                sys = priorSys(hr, lnR)
                dia = priorDia(hr, lnR)
                margin = 15.0
            }
            n < 3 -> {
                val offS = cal.map { it.sys - priorSys(it.hr, ln(max(5.0, it.rmssd))) }.average()
                val offD = cal.map { it.dia - priorDia(it.hr, ln(max(5.0, it.rmssd))) }.average()
                sys = priorSys(hr, lnR) + offS
                dia = priorDia(hr, lnR) + offD
                margin = 12.0
            }
            else -> {
                val f = cal.map { doubleArrayOf(it.hr, ln(max(5.0, it.rmssd))) }
                val s = ridge(f, cal.map { it.sys }, 0.30, -3.0)
                val d = ridge(f, cal.map { it.dia }, 0.20, -2.0)
                sys = s.predict(hr, lnR)
                dia = d.predict(hr, lnR)
                margin = (1.5 * max(s.sd, d.sd) + 4.0).coerceIn(6.0, 18.0)
            }
        }
        sys = sys.coerceIn(70.0, 220.0)
        dia = dia.coerceIn(40.0, 130.0)
        if (sys < dia + 20.0) sys = dia + 20.0
        return BpEstimate(sys, dia, margin, categorize(sys, dia), n >= 1)
    }

    private class Fit(val mean: Double, val s0: Double, val s1: Double, val m0: Double, val m1: Double, val sd: Double) {
        fun predict(hr: Double, lnR: Double) = mean + s0 * (hr - m0) + s1 * (lnR - m1)
    }

    /** Ridge regression on centred features, shrinking slopes toward the population prior. */
    private fun ridge(f: List<DoubleArray>, y: List<Double>, p0: Double, p1: Double): Fit {
        val n = f.size
        val my = y.average()
        val m0 = f.map { it[0] }.average()
        val m1 = f.map { it[1] }.average()
        var a00 = LAMBDA_HR
        var a01 = 0.0
        var a11 = LAMBDA_HRV
        var b0 = LAMBDA_HR * p0
        var b1 = LAMBDA_HRV * p1
        for (i in 0 until n) {
            val x0 = f[i][0] - m0
            val x1 = f[i][1] - m1
            val yy = y[i] - my
            a00 += x0 * x0
            a01 += x0 * x1
            a11 += x1 * x1
            b0 += x0 * yy
            b1 += x1 * yy
        }
        val det = a00 * a11 - a01 * a01
        val s0 = (b0 * a11 - b1 * a01) / det
        val s1 = (a00 * b1 - a01 * b0) / det
        var ss = 0.0
        for (i in 0 until n) {
            val pred = my + s0 * (f[i][0] - m0) + s1 * (f[i][1] - m1)
            ss += (y[i] - pred) * (y[i] - pred)
        }
        return Fit(my, s0, s1, m0, m1, sqrt(ss / max(1, n - 2)))
    }
}
