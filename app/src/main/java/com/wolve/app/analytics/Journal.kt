package com.wolve.app.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

data class Behavior(val id: String, val label: String)

val BEHAVIORS = listOf(
    Behavior("caffeine", "Had caffeine"),
    Behavior("alcohol", "Had alcohol"),
    Behavior("late_meal", "Ate close to bedtime"),
    Behavior("stressed", "Felt stressed"),
    Behavior("hydrated", "Drank enough water"),
    Behavior("late_workout", "Exercised late"),
    Behavior("screens", "Screens before bed"),
    Behavior("meditation", "Meditated or relaxed"),
    Behavior("unwell", "Felt unwell"),
    Behavior("nap", "Took a nap"),
)

data class Effect(val id: String, val label: String, val delta: Double, val withN: Int, val withoutN: Int)

object JournalInsights {
    fun dateOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Compares next-morning recovery on days with and without each behaviour (needs 3 of each). */
    fun effects(journal: Map<String, Set<String>>, nights: List<SleepNight>): List<Effect> {
        val recByWake = HashMap<LocalDate, Int>()
        for (n in nights) recByWake[dateOf(n.wakeTs)] = n.recovery
        val rows = ArrayList<Pair<Set<String>, Int>>()
        for ((d, set) in journal) {
            val date = try { LocalDate.parse(d) } catch (e: Exception) { continue }
            val r = recByWake[date.plusDays(1)] ?: continue
            rows.add(Pair(set, r))
        }
        return BEHAVIORS.mapNotNull { b ->
            val yes = rows.filter { b.id in it.first }.map { it.second }
            val no = rows.filter { b.id !in it.first }.map { it.second }
            if (yes.size >= 3 && no.size >= 3) Effect(b.id, b.label, yes.average() - no.average(), yes.size, no.size) else null
        }.sortedByDescending { abs(it.delta) }
    }
}
