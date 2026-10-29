package com.wolve.app.analytics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Lightweight QRS detector (band-limited derivative, squaring, moving-window integration). */
object Ecg {
    private fun movAvg(x: DoubleArray, w: Int): DoubleArray {
        val n = x.size
        val pre = DoubleArray(n + 1)
        for (i in 0 until n) pre[i + 1] = pre[i] + x[i]
        val h = max(1, w) / 2
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val a = max(0, i - h)
            val b = min(n, i + h + 1)
            out[i] = (pre[b] - pre[a]) / (b - a)
        }
        return out
    }

    /** Returns sample indices of R peaks. Needs at least 3 s of signal. */
    fun detectRPeaks(x: DoubleArray, fs: Double): IntArray {
        val n = x.size
        if (n < (fs * 3).toInt()) return IntArray(0)
        val trend = movAvg(x, (0.5 * fs).toInt())
        val d = DoubleArray(n) { x[it] - trend[it] }
        val sq = DoubleArray(n)
        for (i in 1 until n - 1) {
            val v = d[i + 1] - d[i - 1]
            sq[i] = v * v
        }
        val integ = movAvg(sq, max(1, (0.12 * fs).toInt()))

        val seg = (2 * fs).toInt()
        val nseg = (n + seg - 1) / seg
        val segMax = DoubleArray(nseg)
        for (k in 0 until nseg) {
            var m = 0.0
            for (i in k * seg until min(n, (k + 1) * seg)) if (integ[i] > m) m = integ[i]
            segMax[k] = m
        }

        val peaks = ArrayList<Int>()
        var last = -1_000_000
        val minDist = (0.25 * fs).toInt()
        val r = (0.06 * fs).toInt()
        for (i in 1 until n - 1) {
            if (integ[i] >= integ[i - 1] && integ[i] > integ[i + 1]) {
                val k = i / seg
                var m = 0.0
                for (j in max(0, k - 1)..min(nseg - 1, k + 1)) if (segMax[j] > m) m = segMax[j]
                if (integ[i] > 0.3 * m && i - last >= minDist) {
                    val a = max(0, i - r)
                    val b = min(n, i + r + 1)
                    var best = a
                    for (j in a until b) if (abs(d[j]) > abs(d[best])) best = j
                    if (best - last >= minDist) {
                        peaks.add(best)
                        last = best
                    }
                }
            }
        }
        return peaks.toIntArray()
    }
}
