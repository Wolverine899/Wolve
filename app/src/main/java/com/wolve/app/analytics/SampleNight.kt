package com.wolve.app.analytics

import java.util.Random
import kotlin.math.ln

/** A synthetic night used only to preview the sleep screen. It is never saved. */
object SampleNight {
    fun build(now: Long = System.currentTimeMillis()): SleepNight? {
        val rnd = Random(11)
        val rest = 54.0
        // stage code to (hr offset, hrStd, hrv factor, resp)
        fun params(code: Int): DoubleArray = when (code) {
            0 -> doubleArrayOf(16.0, 1.0, 1.4, 16.0)
            2 -> doubleArrayOf(1.5, 0.55, 1.35, 13.0)
            3 -> doubleArrayOf(8.0, 1.9, 0.65, 15.5)
            else -> doubleArrayOf(6.0, 1.0, 1.0, 14.0)
        }
        val plan = listOf(
            0 to 4, 1 to 6, 2 to 10, 1 to 6, 3 to 4, 1 to 6, 2 to 6, 1 to 6, 3 to 8,
            1 to 6, 0 to 1, 1 to 6, 3 to 10, 1 to 8, 3 to 6, 1 to 4, 0 to 3,
        )
        val total = plan.sumOf { it.second }
        val start = now - total * SleepEngine.BLOCK_MS
        val blocks = ArrayList<SleepBlock>()
        var i = 0
        for ((code, count) in plan) {
            val p = params(code)
            repeat(count) {
                blocks.add(
                    SleepBlock(
                        ts = start + i * SleepEngine.BLOCK_MS,
                        hr = rest + p[0] + rnd.nextGaussian() * 0.8,
                        rmssd = 45.0 * p[2] * (1.0 + rnd.nextGaussian() * 0.08),
                        hrStd = p[1] * Math.abs(1.0 + rnd.nextGaussian() * 0.12),
                        resp = p[3] + rnd.nextGaussian() * 0.3,
                        valid = true,
                    )
                )
                i++
            }
        }
        val night = SleepEngine.analyze(blocks, start, now) ?: return null
        val base = Baselines(10, ln(45.0), 0.02, 54.0, 4.0, 14.0, 0.4, 10)
        val rec = Recovery.score(base, night.hrv, night.rhr, night.resp, night.performance)
        return night.copy(recovery = rec, calibrating = false)
    }
}
