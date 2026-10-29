package com.wolve.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.Coach
import com.wolve.app.analytics.JournalInsights
import com.wolve.app.data.Graph
import com.wolve.app.data.StrainTracker
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun hhmm(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

@Composable
fun CoachScreen(vm: AppViewModel) {
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()
    val strain by StrainTracker.strain.collectAsStateWithLifecycle()

    val nights = sleep.nights
    val last = nights.lastOrNull()
    val plan = Coach.strainTarget(last?.recovery)
    val yesterday = Graph.store.loadDayStrains()[LocalDate.now().minusDays(1).toString()] ?: 0.0
    val need = Coach.sleepNeedMin(yesterday, nights)
    val wakeMin = alarm.hour * 60 + alarm.minute
    val bed = Coach.bedtimeMin(wakeMin, need)

    val insights = ArrayList<String>()
    if (nights.size >= 6) {
        val recent = nights.takeLast(3).map { it.hrv }.average()
        val before = nights.dropLast(3).takeLast(14).map { it.hrv }.average()
        if (before > 0.0) {
            val pct = ((recent - before) / before * 100.0).roundToInt()
            if (abs(pct) >= 5) insights.add(if (pct > 0) "Your HRV is up $pct% over the last three nights." else "Your HRV is down ${abs(pct)}% over the last three nights. Prioritise rest.")
        }
        val rhrNow = nights.takeLast(3).map { it.rhr }.average()
        val rhrBefore = nights.dropLast(3).takeLast(14).map { it.rhr }.average()
        if (rhrNow - rhrBefore >= 3.0) insights.add("Your resting heart rate is about ${(rhrNow - rhrBefore).roundToInt()} bpm higher than usual, a common sign of strain or illness.")
    }
    val effect = JournalInsights.effects(journal, nights).firstOrNull()
    if (effect != null) {
        val dir = if (effect.delta >= 0) "higher" else "lower"
        insights.add("Recovery tends to be ${abs(effect.delta).roundToInt()} points $dir after days with \"${effect.label.lowercase(Locale.US)}\".")
    }
    if (last != null && last.performance < 80) insights.add("You slept ${last.performance}% of your need last night. An earlier bedtime would help.")
    if (insights.isEmpty()) insights.add("Keep tracking nights and logging your journal. Personal insights appear as data builds up.")

    Page {
        Title("Coach", "Rule-based guidance, computed on your phone")

        PCard {
            Text("Today: ${plan.title}", color = Pl.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Target strain ${plan.low} to ${plan.high}", color = Pl.blue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(Pl.card2)) {
                val frac = (strain / 21.0).toFloat().coerceIn(0.02f, 1f)
                Box(Modifier.fillMaxWidth(frac).height(12.dp).clip(RoundedCornerShape(50)).background(Pl.blue))
            }
            Text(
                String.format(Locale.US, "You are at %.1f so far today.", strain),
                color = Pl.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(plan.text, color = Pl.sub, fontSize = 13.sp)
        }

        PCard {
            Text("Tonight's sleep plan", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Aim for ${hm(need)} of sleep.", color = Pl.text, fontSize = 15.sp)
            Text(
                "To wake at ${hhmm(wakeMin)}${if (alarm.enabled) "" else " (default, set in Smart Alarm)"}, be in bed by ${hhmm(bed)}.",
                color = Pl.violet, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "The need is a base of 7.5 hours, plus extra after a hard day and any recent sleep debt.",
                color = Pl.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }

        PCard {
            Text("Insights", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            insights.forEach { Text("•  $it", color = Pl.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        }

        PCard {
            Text(
                "This coach follows simple rules on your own numbers. It is not medical advice. It does not use the internet or an AI service.",
                color = Pl.sub, fontSize = 12.sp,
            )
        }
    }
}
