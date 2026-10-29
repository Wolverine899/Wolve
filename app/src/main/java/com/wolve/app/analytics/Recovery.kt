package com.wolve.app.analytics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Personal rolling baselines (exponentially weighted, roughly two weeks). */
data class Baselines(
    val n: Int = 0,
    val lnHrv: Double = 0.0,
    val lnHrvVar: Double = 0.0,
    val rhr: Double = 0.0,
    val rhrVar: Double = 0.0,
    val resp: Double = 0.0,
    val respVar: Double = 0.0,
    val respN: Int = 0,
)

object Recovery {
    const val CALIBRATION_NIGHTS = 4

    fun isCalibrating(b: Baselines): Boolean = b.n < CALIBRATION_NIGHTS

    fun update(b: Baselines, hrv: Double, rhr: Double, resp: Double): Baselines {
        val x = ln(max(1.0, hrv))
        if (b.n == 0) {
            return Baselines(1, x, 0.0, rhr, 0.0, if (resp > 0.0) resp else 0.0, 0.0, if (resp > 0.0) 1 else 0)
        }
        val a = max(1.0 / (b.n + 1), 1.0 / 14.0)
        val dH = x - b.lnHrv
        val dR = rhr - b.rhr
        var rm = b.resp
        var rv = b.respVar
        var rn = b.respN
        if (resp > 0.0) {
            if (rn == 0) {
                rm = resp
                rv = 0.0
            } else {
                val dP = resp - rm
                rm += a * dP
                rv = (1 - a) * (rv + a * dP * dP)
            }
            rn++
        }
        return Baselines(
            n = b.n + 1,
            lnHrv = b.lnHrv + a * dH,
            lnHrvVar = (1 - a) * (b.lnHrvVar + a * dH * dH),
            rhr = b.rhr + a * dR,
            rhrVar = (1 - a) * (b.rhrVar + a * dR * dR),
            resp = rm,
            respVar = rv,
            respN = rn,
        )
    }

    /** 1..99. Higher HRV, lower resting HR, calmer breathing and enough sleep all raise the score. */
    fun score(b: Baselines, hrv: Double, rhr: Double, resp: Double, sleepPerf: Int): Int {
        val sdH = max(sqrt(b.lnHrvVar), 0.15)
        val sdR = max(sqrt(b.rhrVar), 2.5)
        val sdP = max(sqrt(b.respVar), 0.8)
        val zH = if (b.n == 0) 0.0 else ((ln(max(1.0, hrv)) - b.lnHrv) / sdH).coerceIn(-3.0, 3.0)
        val zR = if (b.n == 0) 0.0 else ((b.rhr - rhr) / sdR).coerceIn(-3.0, 3.0)
        val zP = if (b.n == 0 || resp <= 0.0 || b.respN == 0) 0.0 else ((b.resp - resp) / sdP).coerceIn(-3.0, 3.0)
        val zS = ((sleepPerf - 85) / 15.0).coerceIn(-3.0, 1.0)
        val x = 0.9 * zH + 0.6 * zR + 0.2 * zP + 0.3 * zS + 0.2
        return (100.0 / (1.0 + exp(-1.1 * x))).roundToInt().coerceIn(1, 99)
    }
}
