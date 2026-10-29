package com.wolve.app.ble

import com.wolve.app.data.Live
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic heart rhythm so the app can be explored without hardware. */
object Demo {
    private fun g(d: Double, s: Double, a: Double) = a * exp(-0.5 * (d / s) * (d / s))

    private fun beat(d: Double) =
        g(d, 0.012, 1100.0) - g(d + 0.03, 0.010, 150.0) - g(d - 0.03, 0.010, 200.0) +
            g(d - 0.25, 0.040, 280.0) + g(d + 0.16, 0.025, 130.0)

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.Default) {
        val fs = Live.ECG_FS
        val rnd = Random(7)
        var t = 0.0
        var last = 0.0
        var next = 0.8
        Live.connected.value = true
        Live.status.value = "Demo mode (synthetic signal)"
        while (isActive) {
            val out = IntArray(13)
            for (i in out.indices) {
                t += 1.0 / fs
                if (t >= next) {
                    last = next
                    val rr = 0.86 + 0.04 * sin(2 * PI * 0.25 * t) + (rnd.nextDouble() - 0.5) * 0.04
                    next = last + rr
                    Live.pushRr(rr * 1000.0)
                    Live.pushHr((60.0 / rr).roundToInt())
                }
                val v = beat(t - last) + beat(t - next) + 200.0 * sin(2 * PI * 0.2 * t) + (rnd.nextDouble() - 0.5) * 60.0
                out[i] = v.roundToInt()
            }
            Live.pushEcg(out)
            delay(100)
        }
    }
}
