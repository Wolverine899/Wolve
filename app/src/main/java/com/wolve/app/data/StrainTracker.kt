package com.wolve.app.data

import com.wolve.app.analytics.StrainMath
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import kotlin.math.min

/** Accumulates day strain from every heart-rate sample, even while the screen is off. */
object StrainTracker {
    val strain = MutableStateFlow(0.0)

    private var load = 0.0
    private var lastTs = 0L
    private var lastSave = 0L
    private var day = ""
    private var maxHr = 190.0
    private var restHr = 60.0
    private var male = true
    private var store: Store? = null

    private fun today(): String = LocalDate.now().toString()

    @Synchronized
    fun init(s: Store) {
        store = s
        maxHr = StrainMath.maxHr(s.age)
        restHr = s.restHr
        male = s.male
        val saved = s.loadStrain()
        day = today()
        load = if (saved.first == day) saved.second else 0.0
        lastTs = 0L
        strain.value = StrainMath.scale(load)
    }

    @Synchronized
    fun configure(age: Int, isMale: Boolean, rest: Double) {
        maxHr = StrainMath.maxHr(age)
        male = isMale
        restHr = rest
    }

    @Synchronized
    fun reset() {
        load = 0.0
        lastTs = 0L
        day = today()
        strain.value = 0.0
    }

    @Synchronized
    fun onHr(ts: Long, bpm: Int) {
        val d = today()
        if (d != day) {
            store?.saveDayStrain(day, StrainMath.scale(load))
            day = d
            load = 0.0
            lastTs = 0L
        }
        if (lastTs > 0L && ts > lastTs && maxHr > restHr) {
            val dtMin = min((ts - lastTs) / 60000.0, 10.0 / 60.0)
            val hrr = ((bpm - restHr) / (maxHr - restHr)).coerceIn(0.0, 1.0)
            load += StrainMath.trimp(dtMin, hrr, male)
            strain.value = StrainMath.scale(load)
        }
        lastTs = ts
        if (ts - lastSave > 60_000L) {
            lastSave = ts
            store?.saveStrain(day, load)
            store?.saveDayStrain(day, StrainMath.scale(load))
        }
    }
}
