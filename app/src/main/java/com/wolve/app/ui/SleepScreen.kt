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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.SleepNight
import com.wolve.app.analytics.SleepScore
import com.wolve.app.data.Live
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun clock(ts: Long): String = Fmt.time(ts)

@Composable
fun SleepScreen(vm: AppViewModel) {
    val ui by vm.sleep.collectAsStateWithLifecycle()
    val hr by Live.hr.collectAsStateWithLifecycle()
    val shown = ui.preview ?: ui.last
    val naps by vm.naps.collectAsStateWithLifecycle()

    Page {
        Title("Sleep", "Scored from heart rate, HRV and breathing")

        PCard {
            if (ui.active) {
                Text("Tracking sleep", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Since ${clock(ui.startTs)}, ${hm(ui.elapsedMin)} so far. Heart rate ${if (hr > 0) hr else "--"} bpm.", color = Pl.sub, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.stopSleep() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.amber, contentColor = Pl.bg),
                ) { Text("I'm awake", fontWeight = FontWeight.Bold) }
            } else {
                Text("Track tonight", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Wear your strap, keep this phone nearby, and tap start before bed. Tap again when you wake up.",
                    color = Pl.sub, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.startSleep() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.violet, contentColor = Pl.bg),
                ) { Text("Start sleep tracking", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                if (ui.preview != null) {
                    OutlinedButton(onClick = { vm.closePreview() }, modifier = Modifier.fillMaxWidth()) { Text("Close sample night") }
                } else {
                    OutlinedButton(onClick = { vm.previewSample() }, modifier = Modifier.fillMaxWidth()) { Text("Preview a sample night") }
                }
            }
            if (ui.message.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(ui.message, color = Pl.amber, fontSize = 13.sp)
            }
        }

        PCard {
            Text("Log a nap", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Add a nap you just took. It counts toward today's rest.", color = Pl.sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("20 min", false) { vm.logNap(20) }
                Pill("30 min", false) { vm.logNap(30) }
                Pill("45 min", false) { vm.logNap(45) }
            }
            naps.take(3).forEach { nap ->
                Text("Nap at ${clock(nap.first)}, ${nap.second} min", color = Pl.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        shown?.let { n -> ScoreCard(n) }
        shown?.let { n -> NightCard(n, ui.preview != null) }

        val recent = ui.nights.takeLast(14)
        if (recent.size > 1) {
            PCard {
                Text("Last ${recent.size} nights", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Minutes asleep. The line marks 8 hours.", color = Pl.sub, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                NightBars(recent.map { it.totalMin }, Modifier.fillMaxWidth().height(110.dp))
            }
        }
    }
}

@Composable
private fun NightCard(n: SleepNight, sample: Boolean) {
    PCard {
        Text(if (sample) "Sample night (not saved)" else "Last night", color = Pl.sub, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Ring(n.performance / 100f, Pl.violet, 110.dp, 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${n.performance}%", color = Pl.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("of need", color = Pl.sub, fontSize = 11.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Fact("Asleep", hm(n.totalMin))
                Fact("Efficiency", "${(n.efficiency * 100).roundToInt()}%")
                Fact("Fell asleep after", "${n.latencyMin} min")
            }
        }
        Spacer(Modifier.height(16.dp))
        Hypnogram(n.stages, Modifier.fillMaxWidth().height(110.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(n.onsetTs), color = Pl.sub, fontSize = 12.sp)
            Text(clock(n.wakeTs), color = Pl.sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        StageRow("Awake", n.awakeMin, Pl.amber)
        StageRow("REM", n.remMin, Pl.violet)
        StageRow("Light", n.lightMin, Pl.blue)
        StageRow("Deep", n.deepMin, Pl.deep)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("Resting HR", "${n.rhr.roundToInt()} bpm")
            Fact("HRV", "${n.hrv.roundToInt()} ms")
            Fact("Breathing", if (n.resp > 0.0) String.format(Locale.US, "%.1f /min", n.resp) else "--")
            Fact("Wake-ups", "${n.awakenings}")
        }
        if (n.coverage < 0.7) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Only ${(n.coverage * 100).roundToInt()}% of the night had clean heartbeat data, so this score is less certain.",
                color = Pl.amber, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Sleep times, resting HR, HRV and breathing come straight from your heartbeat data. Stage labels are estimated from heart patterns and are less precise than a sleep lab.",
            color = Pl.sub, fontSize = 12.sp,
        )
    }
}

@Composable
private fun StageRow(label: String, min: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, color = Pl.text, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Text(hm(min), color = Pl.sub, fontSize = 14.sp)
    }
}

@Composable
private fun ScoreCard(n: SleepNight) {
    val parts = SleepScore.of(n)
    val color = when {
        parts.total >= 85 -> Pl.green
        parts.total >= 70 -> Pl.blue
        parts.total >= 55 -> Pl.amber
        else -> Pl.red
    }
    PCard {
        Text("Sleep score", color = Pl.sub, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Ring(parts.total / 100f, color, 110.dp, 11.dp) {
                Text("${parts.total}", color = Pl.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(SleepScore.label(parts.total), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                ScoreBar("Duration", parts.duration, 40, Pl.violet)
                ScoreBar("Efficiency", parts.efficiency, 35, Pl.blue)
                ScoreBar("Disturbances", parts.disturbance, 25, Pl.amber)
            }
        }
        Text(
            "Duration (40) compares sleep with an 8 hour need, efficiency (35) is time asleep while in bed, disturbances (25) lose points for wake-ups and slow sleep onset.",
            color = Pl.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ScoreBar(label: String, value: Int, max: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Pl.text, fontSize = 12.sp)
        Text("$value/$max", color = Pl.sub, fontSize = 12.sp)
    }
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Pl.card2)) {
        Box(Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}
