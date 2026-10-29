package com.wolve.app.analytics

import java.time.LocalDate
import kotlin.math.roundToInt

data class DayRow(val date: LocalDate, val activeMin: Int, val sessions: Int, val recovery: Int?)

data class WeekSummary(
    val activeMin: Int,
    val sessions: Int,
    val kcal: Int,
    val avgStrain: Double,
    val avgSleepPerf: Int,
    val avgRecovery: Int,
    val days: List<DayRow>,
)

object Weekly {
    /** Summary of the 7 days ending on [end] (inclusive). */
    fun summarize(end: LocalDate, workouts: List<Workout>, nights: List<SleepNight>, dayStrains: Map<String, Double>): WeekSummary {
        val dates = (6 downTo 0).map { end.minusDays(it.toLong()) }
        val rows = dates.map { d ->
            val ws = workouts.filter { JournalInsights.dateOf(it.startTs) == d }
            val night = nights.lastOrNull { JournalInsights.dateOf(it.wakeTs) == d }
            DayRow(d, ws.sumOf { ((it.endTs - it.startTs) / 60000L).toInt() }, ws.size, night?.recovery)
        }
        val inWeek = workouts.filter { w -> dates.contains(JournalInsights.dateOf(w.startTs)) }
        val weekNights = nights.filter { n -> dates.contains(JournalInsights.dateOf(n.wakeTs)) }
        val strains = dates.mapNotNull { dayStrains[it.toString()] }
        return WeekSummary(
            activeMin = rows.sumOf { it.activeMin },
            sessions = rows.sumOf { it.sessions },
            kcal = inWeek.sumOf { it.kcal },
            avgStrain = if (strains.isEmpty()) 0.0 else strains.average(),
            avgSleepPerf = if (weekNights.isEmpty()) 0 else weekNights.map { it.performance }.average().roundToInt(),
            avgRecovery = if (weekNights.isEmpty()) 0 else weekNights.map { it.recovery }.average().roundToInt(),
            days = rows,
        )
    }
}
