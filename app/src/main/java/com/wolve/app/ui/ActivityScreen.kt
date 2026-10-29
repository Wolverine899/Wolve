package com.wolve.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.SPORTS
import com.wolve.app.data.Live
import com.wolve.app.data.StrainTracker
import com.wolve.app.data.WorkoutRecorder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun mmss(sec: Int): String = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

@Composable
fun ActivityScreen(vm: AppViewModel) {
    val active by WorkoutRecorder.active.collectAsStateWithLifecycle()
    val live by WorkoutRecorder.live.collectAsStateWithLifecycle()
    val sport by WorkoutRecorder.sportName.collectAsStateWithLifecycle()
    val strain by StrainTracker.strain.collectAsStateWithLifecycle()
    val connected by Live.connected.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf(SPORTS[0]) }
    var msg by remember { mutableStateOf("") }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()) }

    Page {
        Title("Activity", "Day strain and workouts")

        PCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Ring((strain / 21.0).toFloat(), Pl.blue, 150.dp, 14.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.effort(strain), color = Pl.text, fontSize = 40.sp, fontWeight = FontWeight.Black)
                        Text("day strain of 21", color = Pl.sub, fontSize = 12.sp)
                    }
                }
            }
        }

        PCard {
            if (active) {
                Text(sport, color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(mmss(live.elapsedSec), color = Pl.text, fontSize = 48.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Fact("Heart rate", if (live.hr > 0) "${live.hr} bpm" else "--")
                    Fact("Zone", if (live.zone > 0) "${live.zone}" else "below 1")
                    Fact("Average", if (live.avgHr > 0) "${live.avgHr} bpm" else "--")
                    Fact("Calories", "${live.kcal}")
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val w = vm.stopWorkout()
                        msg = if (w == null) "Too short to save (under one minute)." else "Saved. Strain ${String.format(Locale.US, "%.1f", w.strain)}."
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.red, contentColor = Pl.bg),
                ) { Text("Finish activity", fontWeight = FontWeight.Bold) }
            } else {
                Text("Start an activity", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SPORTS.forEach { sp -> Pill(sp, sp == picked) { picked = sp } }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (connected) {
                            msg = ""
                            vm.startWorkout(picked)
                        } else {
                            msg = "Connect your strap first (More, then Devices), or start the demo."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
                ) { Text("Start $picked", fontWeight = FontWeight.Bold) }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, color = Pl.amber, fontSize = 13.sp)
            }
        }

        if (workouts.isNotEmpty()) {
            Text("History", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        workouts.take(15).forEach { w ->
            PCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(w.sport, color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(Instant.ofEpochMilli(w.startTs).atZone(ZoneId.systemDefault()).format(fmt), color = Pl.sub, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Fact("Duration", "${(w.endTs - w.startTs) / 60000L} min")
                    Fact("Strain", String.format(Locale.US, "%.1f", w.strain))
                    Fact("Avg / Max", "${w.avgHr} / ${w.maxHr}")
                    Fact("Calories", "${w.kcal}")
                }
                Spacer(Modifier.height(10.dp))
                ZoneBar(w.zoneSec, Modifier.fillMaxWidth().height(10.dp))
                Text("Time in heart-rate zones 1 to 5", color = Pl.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(onClick = { vm.deleteWorkout(w.startTs) }, modifier = Modifier.padding(top = 8.dp)) { Text("Delete") }
            }
        }
    }
}
