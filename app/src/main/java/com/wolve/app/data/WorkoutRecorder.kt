package com.wolve.app.data

import com.wolve.app.analytics.StrainMath
import com.wolve.app.analytics.Workout
import com.wolve.app.analytics.Zones
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min
import kotlin.math.roundToInt

data class WorkoutLive(val elapsedSec: Int = 0, val hr: Int = 0, val zone: Int = 0, val kcal: Int = 0, val avgHr: Int = 0)

/** Records one activity at a time: heart-rate zones, calories and the strain of the session. */
object WorkoutRecorder {
    val active = MutableStateFlow(false)
    val live = MutableStateFlow(WorkoutLive())
    val sportName = MutableStateFlow("Running")

    private var start = 0L
    private var lastTs = 0L
    private var totalSec = 0.0
    private var sumHr = 0.0
    private var maxHr = 0
    private var kcal = 0.0
    private var load = 0.0
    private val zoneSec = DoubleArray(6)
    private var age = 30
    private var weight = 75.0
    private var male = true
    private var rest = 60.0
    private var hrMax = 190.0

    @Synchronized
    fun start(sport: String, now: Long, age: Int, weightKg: Double, male: Boolean, restHr: Double) {
        this.age = age
        this.weight = weightKg
        this.male = male
        this.rest = restHr
        this.hrMax = StrainMath.maxHr(age)
        start = now
        lastTs = 0L
        totalSec = 0.0
        sumHr = 0.0
        maxHr = 0
        kcal = 0.0
        load = 0.0
        zoneSec.fill(0.0)
        sportName.value = sport
        live.value = WorkoutLive()
        active.value = true
    }

    @Synchronized
    fun onHr(ts: Long, bpm: Int) {
        if (!active.value) return
        if (lastTs > 0L && ts > lastTs) {
            val dt = min((ts - lastTs) / 1000.0, 10.0)
            val zone = Zones.zoneOf(bpm, hrMax)
            zoneSec[zone] += dt
            totalSec += dt
            sumHr += bpm * dt
            kcal += Zones.kcalPerMin(bpm, age, weight, male) * dt / 60.0
            val hrr = ((bpm - rest) / (hrMax - rest)).coerceIn(0.0, 1.0)
            load += StrainMath.trimp(dt / 60.0, hrr, male)
        }
        lastTs = ts
        if (bpm > maxHr) maxHr = bpm
        val avg = if (totalSec > 0.0) (sumHr / totalSec).roundToInt() else bpm
        live.value = WorkoutLive(((ts - start) / 1000L).toInt(), bpm, Zones.zoneOf(bpm, hrMax), kcal.roundToInt(), avg)
    }

    /** Finishes the activity. Returns null when it was shorter than one minute. */
    @Synchronized
    fun stop(now: Long): Workout? {
        if (!active.value) return null
        active.value = false
        if (totalSec < 60.0) return null
        return Workout(
            startTs = start,
            endTs = now,
            sport = sportName.value,
            avgHr = (sumHr / totalSec).roundToInt(),
            maxHr = maxHr,
            kcal = kcal.roundToInt(),
            strain = StrainMath.scale(load),
            zoneSec = zoneSec.map { it.roundToInt() },
        )
    }
}
