package com.wolve.app.data

import com.wolve.app.analytics.Hrv
import com.wolve.app.analytics.Resp
import com.wolve.app.analytics.SleepBlock
import com.wolve.app.analytics.SleepEngine
import com.wolve.app.analytics.Stats
import kotlinx.coroutines.flow.MutableStateFlow

/** Builds 5-minute sleep blocks from the live heart stream. Works with the screen off (foreground service). */
object SleepRecorder {
    val active = MutableStateFlow(false)
    val startTs = MutableStateFlow(0L)

    private val lock = Any()
    private val blocks = ArrayList<SleepBlock>()
    private var curStart = 0L
    private val rrT = ArrayList<Double>()
    private val rrV = ArrayList<Double>()
    private val hrT = ArrayList<Long>()
    private val hrV = ArrayList<Int>()
    private var lastPersist = 0L
    private var store: Store? = null

    fun restore(s: Store) {
        store = s
        val saved = s.loadSleepSession()
        if (saved.first > 0L) {
            synchronized(lock) {
                blocks.clear()
                blocks.addAll(saved.second)
                curStart = saved.first + saved.second.size * SleepEngine.BLOCK_MS
            }
            startTs.value = saved.first
            active.value = true
        }
    }

    fun start(now: Long) {
        synchronized(lock) {
            blocks.clear()
            clearCurrent()
            curStart = now
        }
        lastPersist = now
        startTs.value = now
        active.value = true
        store?.saveSleepSession(now, emptyList())
    }

    fun onHr(ts: Long, bpm: Int) {
        if (!active.value) return
        var snapshot: List<SleepBlock>? = null
        synchronized(lock) {
            roll(ts)
            hrT.add(ts)
            hrV.add(bpm)
            if (ts - lastPersist > 300_000L) {
                lastPersist = ts
                snapshot = ArrayList(blocks)
            }
        }
        snapshot?.let { store?.saveSleepSession(startTs.value, it) }
    }

    fun onRr(ts: Long, ms: Double) {
        if (!active.value) return
        synchronized(lock) {
            roll(ts)
            rrT.add(ts.toDouble())
            rrV.add(ms)
        }
    }

    /** Ends the session and returns its start time and all blocks. */
    fun stop(now: Long): Pair<Long, List<SleepBlock>> {
        val result = synchronized(lock) {
            roll(now)
            if (hrV.size >= 120) finalizeBlock()
            val r = Pair(startTs.value, ArrayList(blocks))
            clearCurrent()
            blocks.clear()
            r
        }
        active.value = false
        store?.clearSleepSession()
        return result
    }

    /** Drops the running session without scoring it. */
    fun discard() {
        synchronized(lock) {
            clearCurrent()
            blocks.clear()
        }
        active.value = false
        store?.clearSleepSession()
    }

    private fun clearCurrent() {
        rrT.clear()
        rrV.clear()
        hrT.clear()
        hrV.clear()
    }

    private fun roll(ts: Long) {
        var guard = 0
        while (ts >= curStart + SleepEngine.BLOCK_MS && guard < 600) {
            finalizeBlock()
            curStart += SleepEngine.BLOCK_MS
            guard++
        }
        if (guard >= 600) curStart = ts
    }

    private fun finalizeBlock() {
        var blk = SleepBlock(curStart, 0.0, 0.0, 0.0, 0.0, false)
        if (hrV.size >= 120 && rrV.size >= 120) {
            val kept = Hrv.keptIndices(rrV)
            val retention = kept.size.toDouble() / rrV.size
            if (kept.size >= 150 && retention >= 0.8) {
                val rr = kept.map { rrV[it] }
                val t = kept.map { rrT[it] }
                val sums = DoubleArray(10)
                val cnt = IntArray(10)
                for (i in hrV.indices) {
                    val k = ((hrT[i] - curStart) / 30_000L).toInt().coerceIn(0, 9)
                    sums[k] += hrV[i].toDouble()
                    cnt[k]++
                }
                val means = ArrayList<Double>()
                for (k in 0 until 10) if (cnt[k] >= 5) means.add(sums[k] / cnt[k])
                if (means.size >= 6) {
                    blk = SleepBlock(
                        ts = curStart,
                        hr = Stats.median(hrV.map { it.toDouble() }),
                        rmssd = Hrv.rmssd(rr),
                        hrStd = Stats.std(means),
                        resp = Resp.estimate(t.toDoubleArray(), rr.toDoubleArray()),
                        valid = true,
                    )
                }
            }
        }
        blocks.add(blk)
        clearCurrent()
    }
}
