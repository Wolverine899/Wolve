package com.wolve.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.Weekly
import com.wolve.app.data.Graph
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun WeeklyRecapScreen(vm: AppViewModel) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    var offset by remember { mutableStateOf(0) }

    val end = LocalDate.now().minusDays(7L * offset)
    val sum = Weekly.summarize(end, workouts, sleep.nights, Graph.store.loadDayStrains())
    val minFrac = if (goals.minutes > 0) min(1.0, sum.activeMin.toDouble() / goals.minutes) else 0.0
    val sesFrac = if (goals.sessions > 0) min(1.0, sum.sessions.toDouble() / goals.sessions) else 0.0
    val score = ((minFrac + sesFrac) / 2.0 * 100).roundToInt()
    val bestStrain = workouts.filter { w -> sum.days.any { d -> com.wolve.app.analytics.JournalInsights.dateOf(w.startTs) == d.date } }.maxOfOrNull { it.strain } ?: 0.0

    Page {
        Title("Weekly recap", "${end.minusDays(6)} to $end")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("This week", offset == 0) { offset = 0 }
            Pill("Last week", offset == 1) { offset = 1 }
        }

        PCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Ring(score / 100f, if (score >= 100) Pl.green else Pl.blue, 110.dp, 11.dp) {
                    Text("$score%", color = Pl.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                Column {
                    Text("Goal progress", color = Pl.sub, fontSize = 13.sp)
                    Text(
                        when {
                            score >= 100 -> "Goals reached. Great week."
                            score >= 60 -> "Solid week, keep going."
                            else -> "Room to move more."
                        },
                        color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        PCard {
            GoalRow("Active minutes", "${sum.activeMin} of ${goals.minutes} min", minFrac.toFloat(), Pl.green)
            Spacer(Modifier.height(12.dp))
            GoalRow("Workouts", "${sum.sessions} of ${goals.sessions}", sesFrac.toFloat(), Pl.blue)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Calories", "${sum.kcal}")
                Fact("Avg strain", String.format(Locale.US, "%.1f", sum.avgStrain))
                Fact("Avg recovery", if (sum.avgRecovery > 0) "${sum.avgRecovery}%" else "--")
                Fact("Sleep", if (sum.avgSleepPerf > 0) "${sum.avgSleepPerf}%" else "--")
            }
        }

        PCard {
            Text("Daily", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                sum.days.forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = Pl.sub, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(readinessColor(d.recovery ?: 0)))
                        Spacer(Modifier.height(6.dp))
                        Text("${d.activeMin}", color = Pl.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("min", color = Pl.sub, fontSize = 10.sp)
                    }
                }
            }
            Text("Dots show that morning's recovery.", color = Pl.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        PCard {
            Text("Achievements", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Badge("Active Centurion", "200 or more active minutes", sum.activeMin >= 200)
            Badge("Iron Frequency", "4 or more workouts", sum.sessions >= 4)
            Badge("Peak Aerobic Load", "A workout with strain 15 or more", bestStrain >= 15.0)
            Badge("Sleep Anchor", "Average sleep performance 85% or more", sum.avgSleepPerf >= 85)
        }

        PCard {
            Text("Weekly goals", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Active minutes: ${goals.minutes}", color = Pl.text, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setGoals(goals.copy(minutes = (goals.minutes - 15).coerceAtLeast(30))) }) { Text("-15") }
                    OutlinedButton(onClick = { vm.setGoals(goals.copy(minutes = (goals.minutes + 15).coerceAtMost(600))) }) { Text("+15") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Workouts: ${goals.sessions}", color = Pl.text, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setGoals(goals.copy(sessions = (goals.sessions - 1).coerceAtLeast(1))) }) { Text("-1") }
                    OutlinedButton(onClick = { vm.setGoals(goals.copy(sessions = (goals.sessions + 1).coerceAtMost(14))) }) { Text("+1") }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(label: String, value: String, frac: Float, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Pl.text, fontSize = 14.sp)
        Text(value, color = Pl.sub, fontSize = 14.sp)
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Pl.card2)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
private fun Badge(title: String, subtitle: String, earned: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (earned) Pl.green else Pl.card2))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = if (earned) Pl.text else Pl.sub, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Pl.sub, fontSize = 12.sp)
        }
    }
}
