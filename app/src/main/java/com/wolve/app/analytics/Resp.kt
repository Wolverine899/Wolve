package com.wolve.app.analytics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Respiratory rate from respiratory sinus arrhythmia in the R-R series (needs about 5 minutes of clean data). */
object Resp {
    private const val FS = 4.0
    private const val F_MIN = 0.10
    private const val F_MAX = 0.45
    private const val F_STEP = 0.005

    /** [tMs] ascending timestamps of each beat, [rrMs] the matching intervals. Returns breaths/min, or 0.0 if unreliable. */
    fun estimate(tMs: DoubleArray, rrMs: DoubleArray): Double {
        val m = min(tMs.size, rrMs.size)
        if (m < 100) return 0.0
        val t0 = tMs[0]
        val dur = (tMs[m - 1] - t0) / 1000.0
        if (dur < 120.0) return 0.0
        val n = (dur * FS).toInt()
        if (n < 64) return 0.0

        // 1) resample to an even 4 Hz grid by linear interpolation
        val x = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val tt = t0 + i * 1000.0 / FS
            while (j < m - 2 && tMs[j + 1] < tt) j++
            val span = tMs[j + 1] - tMs[j]
            val f = if (span <= 0.0) 0.0 else ((tt - tMs[j]) / span).coerceIn(0.0, 1.0)
            x[i] = rrMs[j] + f * (rrMs[j + 1] - rrMs[j])
        }

        // 2) remove slow trend (12 s moving average)
        val pre = DoubleArray(n + 1)
        for (i in 0 until n) pre[i + 1] = pre[i] + x[i]
        val h = 24
        for (i in 0 until n) {
            val a = max(0, i - h)
            val b = min(n, i + h + 1)
            x[i] -= (pre[b] - pre[a]) / (b - a)
        }

        // 3) Hann window then direct DFT over the respiratory band
        for (i in 0 until n) x[i] *= 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
        var best = 0.0
        var bestP = 0.0
        var total = 0.0
        val steps = ((F_MAX - F_MIN) / F_STEP).toInt()
        for (s in 0..steps) {
            val f = F_MIN + s * F_STEP
            var re = 0.0
            var im = 0.0
            val w = 2.0 * PI * f / FS
            for (i in 0 until n) {
                re += x[i] * cos(w * i)
                im += x[i] * sin(w * i)
            }
            val p = re * re + im * im
            total += p
            if (p > bestP) {
                bestP = p
                best = f
            }
        }
        if (total <= 0.0 || bestP / total < 0.12) return 0.0
        return best * 60.0
    }
}
