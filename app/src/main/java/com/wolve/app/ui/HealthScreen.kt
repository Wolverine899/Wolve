package com.wolve.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.SleepNight
import com.wolve.app.analytics.Stats
import java.util.Locale
import kotlin.math.max

@Composable
fun HealthScreen(vm: AppViewModel, go: (String) -> Unit) {
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val nights = sleep.nights.takeLast(30)
    val latest = nights.lastOrNull()
    val history = if (nights.size > 1) nights.dropLast(1) else emptyList()

    Page {
        Title("Health", "Last night against your usual range")

        PCard {
            Text("Health Monitor", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (latest == null) {
                Text("Track a night of sleep to see your vitals here.", color = Pl.sub, fontSize = 13.sp)
            } else {
                MonitorRow("Heart rate variability", latest.hrv, "ms", history.map { it.hrv }, 0)
                MonitorRow("Resting heart rate", latest.rhr, "bpm", history.map { it.rhr }, 0)
                if (latest.resp > 0.0) MonitorRow("Respiratory rate", latest.resp, "/min", history.map { it.resp }.filter { it > 0.0 }, 1)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (history.size < 4) "Your range appears after about four nights." else "Green band is your usual range over the last ${history.size} nights.",
                    color = Pl.sub, fontSize = 12.sp,
                )
            }
        }

        PCard {
            Text("Not available over Bluetooth", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Blood oxygen and skin temperature come from a WHOOP strap's own optical and thermal sensors and are not shared over standard Bluetooth, so Wolve cannot show them.",
                color = Pl.sub, fontSize = 13.sp,
            )
        }

        MenuRow("☤", "Body", "Live heartbeat and blood circulation") { go("body") }
        MenuRow("♥", "Live heart and ECG", "Heart rate, ECG trace and HRV in real time") { go("heart") }
        MenuRow("⌁", "Blood pressure estimate", "60-second estimate from heart rhythm") { go("pressure") }
        MenuRow("◐", "Stress monitor", "Two-minute stress scores through the day") { go("stress") }
        MenuRow("≋", "Trends", "7 and 30 day charts") { go("trends") }
    }
}

@Composable
private fun MonitorRow(label: String, value: Double, unit: String, past: List<Double>, decimals: Int) {
    val shown = String.format(Locale.US, "%.${decimals}f", value)
    val ready = past.size >= 4
    val mean = if (ready) past.average() else value
    val sd = max(Stats.std(past), max(0.05 * mean, 0.5))
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Pl.text, fontSize = 14.sp)
            Text("$shown $unit", color = Pl.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        if (ready) {
            RangeBar(value, mean, sd, Modifier.fillMaxWidth().height(18.dp))
            val state = when {
                value > mean + sd -> "Above your usual range"
                value < mean - sd -> "Below your usual range"
                else -> "Within your usual range"
            }
            Text(state, color = Pl.sub, fontSize = 12.sp)
        } else {
            Text("Calibrating", color = Pl.sub, fontSize = 12.sp)
        }
    }
}
