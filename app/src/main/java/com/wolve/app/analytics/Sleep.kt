package com.wolve.app.analytics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One 5-minute window of overnight data. [valid] is false when the signal was missing or too noisy. */
data class SleepBlock(
    val ts: Long,
    val hr: Double,
    val rmssd: Double,
    val hrStd: Double,
    val resp: Double,
    val valid: Boolean,
)

data class SleepNight(
    val startTs: Long,
    val endTs: Long,
    val onsetTs: Long,
    val wakeTs: Long,
    val totalMin: Int,
    val awakeMin: Int,
    val lightMin: Int,
    val deepMin: Int,
    val remMin: Int,
    val efficiency: Double,
    val latencyMin: Int,
    val awakenings: Int,
    val rhr: Double,
    val hrv: Double,
    val resp: Double,
    val coverage: Double,
    val performance: Int,
    val recovery: Int,
    val calibrating: Boolean,
    /** One digit per 5-minute block from [onsetTs]: 0 awake, 1 light, 2 deep, 3 REM. */
    val stages: String,
)

/**
 * Sleep scoring from heart rate and HRV only (no motion sensor).
 * Sleep timing, resting HR, HRV and respiratory rate are well supported by chest-strap data.
 * Stage labels are an estimate from HR/HRV patterns and are less reliable than lab polysomnography.
 */
object SleepEngine {
    const val BLOCK_MS = 300_000L
    const val BLOCK_MIN = 5
    const val NEED_MIN = 480

    private const val W = 0
    private const val L = 1
    private const val D = 2
    private const val R = 3

    fun analyze(blocks: List<SleepBlock>, startTs: Long, endTs: Long, deepHrv: Boolean = false): SleepNight? {
        val valid = blocks.filter { it.valid }
        if (valid.size < 6) return null

        val rest = valid.map { it.hr }.sorted().take(3).average()
        val thrSleep = rest * 1.15 + 2.5
        val thrWake = rest * 1.2 + 3.0
        val n = blocks.size

        var onset = -1
        for (i in 0..(n - 3)) {
            if (ok(blocks[i], thrSleep) && ok(blocks[i + 1], thrSleep) && ok(blocks[i + 2], thrSleep)) {
                onset = i
                break
            }
        }
        if (onset < 0) return null
        var end = -1
        for (j in (n - 1) downTo onset) {
            if (ok(blocks[j], thrWake)) {
                end = j
                break
            }
        }
        if (end < onset) return null

        val inRange = (onset..end).map { blocks[it] }.filter { it.valid }
        val medH = Stats.median(inRange.map { it.rmssd }).let { if (it <= 0.0) 1.0 else it }
        val medS = Stats.median(inRange.map { it.hrStd }).let { if (it <= 0.0) 1.0 else it }

        val len = end - onset + 1
        val st = IntArray(len)
        var prev = L
        for (i in onset..end) {
            val b = blocks[i]
            val s = if (!b.valid) {
                prev
            } else if (b.hr > thrWake) {
                W
            } else {
                val hrRel = (b.hr - rest) / rest
                val hv = b.rmssd / medH
                val sv = b.hrStd / medS
                val pos = (i - onset).toDouble() / len
                if (hrRel < 0.07 && sv < 0.95 && hv >= 0.95 && pos < 0.7) D
                else if (sv > 1.2 && hv < 1.0 && pos > 0.15 && hrRel >= 0.03) R
                else L
            }
            st[i - onset] = s
            prev = s
        }

        // smooth isolated single-block flips between the same neighbours (never touches wake)
        val sm = st.copyOf()
        for (i in 1 until len - 1) {
            if (st[i] != W && st[i - 1] == st[i + 1] && st[i - 1] != W) sm[i] = st[i - 1]
        }

        // physiological caps so one noisy night cannot report an implausible architecture
        val sleepCount = sm.count { it != W }
        capStage(sm, D, (sleepCount * 0.30).toInt(), blocks, onset, true)
        capStage(sm, R, (sleepCount * 0.35).toInt(), blocks, onset, false)

        var light = 0
        var deep = 0
        var rem = 0
        var awake = 0
        var awakenings = 0
        for (i in 0 until len) {
            when (sm[i]) {
                L -> light++
                D -> deep++
                R -> rem++
                else -> awake++
            }
            if (sm[i] == W && i > 0 && sm[i - 1] != W) awakenings++
        }
        val sleepBlocks = light + deep + rem

        val asleepBlocks = (onset..end).map { blocks[it] }.filterIndexed { k, b -> b.valid && sm[k] != W }
        val deepBlocks = (onset..end).map { blocks[it] }.filterIndexed { k, b -> b.valid && sm[k] == D }
        val hrv = Stats.median((if (deepHrv && deepBlocks.isNotEmpty()) deepBlocks else asleepBlocks).map { it.rmssd })
        val resp = Stats.median(asleepBlocks.map { it.resp }.filter { it > 0.0 })

        val onsetTs = blocks[onset].ts
        val wakeTs = blocks[end].ts + BLOCK_MS
        val inBedMin = max(1L, (wakeTs - startTs) / 60000L).toInt()
        val totalMin = sleepBlocks * BLOCK_MIN

        return SleepNight(
            startTs = startTs,
            endTs = endTs,
            onsetTs = onsetTs,
            wakeTs = wakeTs,
            totalMin = totalMin,
            awakeMin = awake * BLOCK_MIN,
            lightMin = light * BLOCK_MIN,
            deepMin = deep * BLOCK_MIN,
            remMin = rem * BLOCK_MIN,
            efficiency = min(1.0, totalMin.toDouble() / inBedMin),
            latencyMin = max(0L, (onsetTs - startTs) / 60000L).toInt(),
            awakenings = awakenings,
            rhr = rest,
            hrv = hrv,
            resp = resp,
            coverage = valid.size.toDouble() / blocks.size,
            performance = (totalMin * 100.0 / NEED_MIN).roundToInt().coerceIn(0, 100),
            recovery = 0,
            calibrating = true,
            stages = sm.joinToString("") { it.toString() },
        )
    }

    private fun ok(b: SleepBlock, thr: Double) = b.valid && b.hr <= thr

    /** Demotes the least typical blocks of [stage] to light sleep until it fits under [maxCount]. */
    private fun capStage(sm: IntArray, stage: Int, maxCount: Int, blocks: List<SleepBlock>, onset: Int, byHighHr: Boolean) {
        val idx = sm.indices.filter { sm[it] == stage }
        if (idx.size <= maxCount) return
        val ordered = if (byHighHr) idx.sortedByDescending { blocks[onset + it].hr } else idx.sortedBy { blocks[onset + it].hrStd }
        for (k in 0 until (idx.size - maxCount)) sm[ordered[k]] = L
    }
}
