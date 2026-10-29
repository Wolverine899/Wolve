package com.wolve.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.Coach
import com.wolve.app.analytics.JournalInsights
import com.wolve.app.analytics.Nutrition
import com.wolve.app.analytics.StrainMath
import com.wolve.app.analytics.StressMath
import com.wolve.app.analytics.Zones
import com.wolve.app.data.Live
import com.wolve.app.data.StrainTracker
import com.wolve.app.data.StressTracker
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: AppViewModel, go: (String) -> Unit) {
    val m by vm.metrics.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val strain by StrainTracker.strain.collectAsStateWithLifecycle()
    val stress by StressTracker.today.collectAsStateWithLifecycle()
    val hr by Live.hr.collectAsStateWithLifecycle()
    val series by Live.hrSeries.collectAsStateWithLifecycle()
    val status by Live.status.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val water by vm.hydration.collectAsStateWithLifecycle()

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val today = LocalDate.now()
    val date = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    val night = sleep.last
    val plan = Coach.strainTarget(night?.recovery)
    val todays = workouts.filter { JournalInsights.dateOf(it.startTs) == today }
    val lastStress = stress.lastOrNull { it.score >= 0.0 }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val av = UiPrefs.avatar
            if (av != null) {
                androidx.compose.foundation.Image(av, null, Modifier.size(60.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Logo(60.dp)
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text("WOLVE", color = Pl.text, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                Text("$greeting, $date", color = Pl.sub, fontSize = 13.sp)
            }
        }

        PCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val rec = night?.recovery ?: 0
                RingStat("Recovery", if (night != null) "$rec%" else "--", rec / 100f, readinessColor(rec))
                RingStat("Effort", Fmt.effort(strain), (strain / 21.0).toFloat(), Pl.blue)
                val perf = night?.performance ?: 0
                RingStat("Sleep", if (night != null) "$perf%" else "--", perf / 100f, Pl.violet)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    night == null -> "Track a night of sleep to unlock your recovery score."
                    night.calibrating -> "Calibrating your baselines: ${sleep.baselineNights} of 4 nights. Scores sharpen after that."
                    else -> "Recovery compares last night's HRV, resting heart rate and breathing with your own baselines."
                },
                color = Pl.sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }

        PCard {
            val maxHr = StrainMath.maxHr(profile.age)
            val zone = if (hr > 0) Zones.zoneOf(hr, maxHr) else 0
            val zoneName = listOf("Resting", "Warm-up", "Fat burn", "Cardio", "Threshold", "Peak")[zone]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Heart-rate zone", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (hr > 0) "$zoneName  ·  $hr bpm" else "No signal", color = zoneColor(zone), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            ZoneSpectrum(hr, maxHr, Modifier.fillMaxWidth().height(34.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Warm-up", color = Pl.sub, fontSize = 10.sp)
                Text("Fat burn", color = Pl.sub, fontSize = 10.sp)
                Text("Cardio", color = Pl.sub, fontSize = 10.sp)
                Text("Threshold", color = Pl.sub, fontSize = 10.sp)
                Text("Peak", color = Pl.sub, fontSize = 10.sp)
            }
        }

        if (UiPrefs.hydrationOn) PCard {
            val goal = Nutrition.plan(profile.age, profile.weightKg, profile.heightCm, profile.male, strain).waterMl
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hydration", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$water of $goal ml", color = Pl.blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.addWater(250) }) { Text("+250 ml") }
                OutlinedButton(onClick = { vm.addWater(500) }) { Text("+500 ml") }
                OutlinedButton(onClick = { vm.addWater(-250) }) { Text("-250") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PCard(Modifier.weight(1f).clickable { go("weekly") }) {
                Text("Weekly recap", color = Pl.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Goals and achievements", color = Pl.sub, fontSize = 12.sp)
            }
            PCard(Modifier.weight(1f).clickable { go("breath") }) {
                Text("Breathwork", color = Pl.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Calm down in 2 minutes", color = Pl.sub, fontSize = 12.sp)
            }
        }

        PCard(Modifier.clickable { go("coach") }) {
            Text("Coach  ·  ${plan.title}", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Today's strain target ${plan.low} to ${plan.high}. You are at ${String.format(Locale.US, "%.1f", strain)}.", color = Pl.sub, fontSize = 13.sp)
        }

        PCard(Modifier.clickable { go("tab:2") }) {
            Text("Today's activities", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (todays.isEmpty()) {
                Text("None yet. Tap to start an activity.", color = Pl.sub, fontSize = 13.sp)
            } else {
                todays.forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(w.sport, color = Pl.text, fontSize = 14.sp)
                        Text("${((w.endTs - w.startTs) / 60000L)} min   strain ${String.format(Locale.US, "%.1f", w.strain)}", color = Pl.sub, fontSize = 13.sp)
                    }
                }
            }
        }

        PCard(Modifier.clickable { go("stress") }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stress monitor", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (lastStress != null) {
                    Text(StressMath.label(lastStress.score), color = stressColor(lastStress.score), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (stress.isEmpty()) {
                Text("Needs a few minutes of heartbeat data.", color = Pl.sub, fontSize = 13.sp)
            } else {
                StressBars(stress.takeLast(90), Modifier.fillMaxWidth().height(56.dp))
            }
        }

        if (UiPrefs.journalReminder && hour >= 18 && (vm.journal.value[today.toString()] ?: emptySet()).isEmpty()) {
            MenuRow("✎", "Log today's journal", "A minute now shows tomorrow's recovery what mattered") { go("journal") }
        }
        if (UiPrefs.illnessHeadsUp && night != null) {
            val b = com.wolve.app.data.Graph.store.loadBaselines()
            val rhrHigh = b.n >= 4 && night.rhr > b.rhr + 1.5 * kotlin.math.max(kotlin.math.sqrt(b.rhrVar), 2.0)
            val hrvLow = b.n >= 4 && kotlin.math.ln(kotlin.math.max(1.0, night.hrv)) < b.lnHrv - 1.5 * kotlin.math.max(kotlin.math.sqrt(b.lnHrvVar), 0.15)
            if (rhrHigh && hrvLow) {
                PCard {
                    Text("Heads-up", color = Pl.amber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Your resting heart rate is up and your HRV is down compared with your usual. This pattern can show up before you feel unwell. Rest, hydrate, and see a clinician if you have symptoms. This is an observation, not a diagnosis.", color = Pl.sub, fontSize = 13.sp)
                }
            }
        }
        MenuRow("✎", "Journal", "Log today's habits to see what moves your recovery") { go("journal") }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PCard(Modifier.weight(1f)) {
                Text("Heart rate", color = Pl.sub, fontSize = 14.sp)
                BigNumber(if (hr > 0) "$hr" else "--", "bpm", Pl.red, 40)
                Spacer(Modifier.height(8.dp))
                Sparkline(series.takeLast(90).map { it.bpm.toFloat() }, Pl.red, Modifier.fillMaxWidth().height(64.dp))
            }
            Tile("HRV now", if (m.rmssd > 0) "${m.rmssd.roundToInt()}" else "--", "ms", Modifier.weight(1f))
        }
        Text(status, color = Pl.sub, fontSize = 12.sp)
    }
}

@Composable
private fun RingStat(label: String, value: String, progress: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(progress, color, 92.dp, 9.dp) {
            Text(value, color = Pl.text, fontSize = 20.sp, fontWeight = if (UiPrefs.softGauge) FontWeight.Normal else FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Pl.sub, fontSize = 13.sp)
    }
}

@Composable
internal fun Fact(label: String, value: String) {
    Column {
        Text(label, color = Pl.sub, fontSize = 12.sp)
        Text(value, color = Pl.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
