package com.wolve.app.data

import com.wolve.app.analytics.Hrv
import com.wolve.app.analytics.Stats
import com.wolve.app.analytics.StressMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.ln
import kotlin.math.max

/** Scores stress every two minutes from HRV versus your own daytime baseline. Hard exercise is not counted as stress. */
object StressTracker {
    private const val WINDOW_MS = 120_000L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** score = -1 marks a window dominated by physical activity. */
    val today = MutableStateFlow<List<StressPoint>>(emptyList())

    private val lock = Any()
    private val rr = ArrayList<Double>()
    private val hr = ArrayList<Int>()
    private var winStart = 0L
    private var store: Store? = null
    private var mean = 0.0
    private var variance = 0.0
    private var n = 0
    private var lastCheckin = 0L

    fun init(s: Store) {
        store = s
        val b = s.loadStressBase()
        mean = b.first
        variance = b.second
        n = b.third
        val cutoff = System.currentTimeMillis() - DAY_MS
        today.value = s.loadStress().filter { it.ts >= cutoff }
    }

    fun clear() {
        synchronized(lock) {
            rr.clear()
            hr.clear()
            winStart = 0L
            mean = 0.0
            variance = 0.0
            n = 0
        }
        today.value = emptyList()
    }

    fun onHr(ts: Long, bpm: Int) {
        synchronized(lock) {
            roll(ts)
            hr.add(bpm)
        }
    }

    fun onRr(ts: Long, ms: Double) {
        synchronized(lock) {
            roll(ts)
            rr.add(ms)
        }
    }

    private fun roll(ts: Long) {
        if (winStart == 0L) winStart = ts
        if (ts - winStart < WINDOW_MS) return
        finalizeWindow(winStart)
        rr.clear()
        hr.clear()
        winStart = ts
    }

    private fun finalizeWindow(start: Long) {
        if (hr.size < 60 || rr.size < 60) return
        val clean = Hrv.clean(rr)
        if (clean.size < 60) return
        val rest = store?.restHr ?: 60.0
        val bpm = Stats.median(hr.map { it.toDouble() })
        val rmssd = Hrv.rmssd(clean)
        val score: Double
        if (bpm > rest + 45.0) {
            score = -1.0
        } else {
            val x = ln(max(1.0, rmssd))
            if (n == 0) {
                mean = x
                variance = 0.0
            } else {
                val a = max(1.0 / (n + 1), 0.02)
                val d = x - mean
                mean += a * d
                variance = (1 - a) * (variance + a * d * d)
            }
            n++
            store?.saveStressBase(mean, variance, n)
            score = StressMath.score(rmssd, mean, variance)
        }
        if (score >= 2.4 && com.wolve.app.ui.UiPrefs.stressCheckins && start - lastCheckin > 2L * 60L * 60L * 1000L) {
            lastCheckin = start
            com.wolve.app.service.notifyUser(5, "wolve_zones", "Take a minute to breathe", "Your HRV dipped while you were still. Open Wolve > Breathwork.")
        }
        val cutoff = start - DAY_MS
        val list = (today.value.filter { it.ts >= cutoff } + StressPoint(start, score)).takeLast(720)
        today.value = list
        store?.saveStress(list)
    }
}
