package com.wolve.app.analytics

import kotlin.math.abs
import kotlin.math.sqrt

object Stats {
    fun median(x: List<Double>): Double {
        if (x.isEmpty()) return 0.0
        val s = x.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    fun std(x: List<Double>): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / (x.size - 1))
    }
}

object Hrv {
    /** Indices of intervals kept: physiologically possible and within 25% of the median. */
    fun keptIndices(rr: List<Double>): List<Int> {
        val idx = rr.indices.filter { rr[it] in 300.0..2000.0 }
        if (idx.size < 5) return idx
        val med = Stats.median(idx.map { rr[it] })
        return idx.filter { abs(rr[it] - med) <= 0.25 * med }
    }

    fun clean(rr: List<Double>): List<Double> = keptIndices(rr).map { rr[it] }

    fun mean(x: List<Double>): Double = if (x.isEmpty()) 0.0 else x.sum() / x.size

    fun meanHr(rr: List<Double>): Double = if (rr.isEmpty()) 0.0 else 60000.0 / mean(rr)

    fun sdnn(rr: List<Double>): Double = Stats.std(rr)

    fun rmssd(rr: List<Double>): Double {
        if (rr.size < 2) return 0.0
        var s = 0.0
        for (i in 1 until rr.size) {
            val d = rr[i] - rr[i - 1]
            s += d * d
        }
        return sqrt(s / (rr.size - 1))
    }

    fun pnn50(rr: List<Double>): Double {
        if (rr.size < 2) return 0.0
        var c = 0
        for (i in 1 until rr.size) if (abs(rr[i] - rr[i - 1]) > 50.0) c++
        return 100.0 * c / (rr.size - 1)
    }
}
