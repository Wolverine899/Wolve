package com.wolve.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.BpCategory
import com.wolve.app.analytics.StrainMath
import com.wolve.app.analytics.Zones
import com.wolve.app.data.Live
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val screenPad = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)

@Composable
internal fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(screenPad),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

// ------------------------------------------------------------------ Heart

@Composable
fun HeartScreen(vm: AppViewModel) {
    val m by vm.metrics.collectAsStateWithLifecycle()
    val hr by Live.hr.collectAsStateWithLifecycle()
    val ecg by Live.ecgView.collectAsStateWithLifecycle()
    val hasEcg by Live.ecgAvailable.collectAsStateWithLifecycle()
    val connected by Live.connected.collectAsStateWithLifecycle()
    val prof by vm.profile.collectAsStateWithLifecycle()
    val zone = if (hr > 0) Zones.zoneOf(hr, StrainMath.maxHr(prof.age)) else 0

    Page {
        Title("Heart", if (connected) "Live" else "No device connected")
        PCard {
            Text("Heart rate", color = Pl.sub, fontSize = 14.sp)
            BigNumber(if (hr > 0) "$hr" else "--", "bpm", Pl.red, 72)
            Text(
                if (zone > 0) "Zone $zone of 5" else "Below zone 1",
                color = zoneColor(zone), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        PCard {
            Text("ECG, last 8 seconds", color = Pl.sub, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            EcgTrace(ecg, Modifier.fillMaxWidth().height(170.dp))
            if (!hasEcg) {
                Spacer(Modifier.height(8.dp))
                Text("No ECG stream. Use an ECG-capable chest strap (for example Polar H10) or the demo.", color = Pl.sub, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tile("RMSSD", if (m.rmssd > 0) "${m.rmssd.roundToInt()}" else "--", "ms", Modifier.weight(1f))
            Tile("SDNN", if (m.sdnn > 0) "${m.sdnn.roundToInt()}" else "--", "ms", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tile("pNN50", if (m.beats > 0) "${m.pnn50.roundToInt()}" else "--", "%", Modifier.weight(1f))
            Tile("Clean beats", "${m.beats}", "", Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ Blood pressure

private fun catColor(label: String): Color = when (BpCategory.values().firstOrNull { it.label == label }) {
    BpCategory.NORMAL -> Pl.green
    BpCategory.ELEVATED -> Pl.amber
    BpCategory.STAGE1 -> Pl.orange
    else -> Pl.red
}

@Composable
fun BpScreen(vm: AppViewModel) {
    val ui by vm.bp.collectAsStateWithLifecycle()
    val hasEcg by Live.ecgAvailable.collectAsStateWithLifecycle()
    var sysTxt by remember { mutableStateOf("") }
    var diaTxt by remember { mutableStateOf("") }

    Page {
        Title("Blood pressure", "Estimated from your heart rhythm")

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pl.amber.copy(alpha = 0.12f)).padding(14.dp)
        ) {
            Text(
                "Estimate only, not a medical device. It cannot diagnose or monitor hypertension. " +
                    "Use a validated cuff for any decision, and get urgent care for chest pain, severe headache or fainting.",
                color = Pl.amber, fontSize = 13.sp,
            )
        }

        PCard {
            if (ui.phase == Phase.MEASURING) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Ring(ui.progress, Pl.violet, 180.dp, 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(MEASURE_SEC * (1 - ui.progress)).roundToInt()}", color = Pl.text, fontSize = 52.sp, fontWeight = FontWeight.Black)
                            Text("seconds left", color = Pl.sub, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Sit still, rest your arm, and breathe normally.", color = Pl.sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.cancelBp() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            } else {
                Text("New estimate", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Takes ${MEASURE_SEC} seconds. Source: ${if (hasEcg) "ECG" else "heart-rate intervals"}.",
                    color = Pl.sub, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.startBp() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.violet, contentColor = Pl.bg),
                ) { Text("Measure for ${MEASURE_SEC} s", fontWeight = FontWeight.Bold) }
                if (ui.phase == Phase.ERROR && ui.message.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(ui.message, color = Pl.red, fontSize = 13.sp)
                }
            }
        }

        ui.latest?.let { r ->
            if (ui.phase != Phase.MEASURING) {
                PCard {
                    Text("Latest estimate", color = Pl.sub, fontSize = 14.sp)
                    BigNumber("${r.sys.roundToInt()}/${r.dia.roundToInt()}", "mmHg", catColor(r.category), 56)
                    Text("plus or minus about ${r.margin.roundToInt()} mmHg", color = Pl.sub, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        r.category,
                        color = Pl.bg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(catColor(r.category)).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column { Text("Heart rate", color = Pl.sub, fontSize = 12.sp); Text("${r.hr.roundToInt()} bpm", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Column { Text("HRV", color = Pl.sub, fontSize = 12.sp); Text("${r.rmssd.roundToInt()} ms", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Column { Text("Signal", color = Pl.sub, fontSize = 12.sp); Text("${(r.quality * 100).roundToInt()}%", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (r.calibrated) "Tuned with ${ui.calCount} cuff reading(s)." else "Not tuned yet, so accuracy is low. Add cuff readings below.",
                        color = Pl.sub, fontSize = 12.sp,
                    )
                    if (r.category == BpCategory.CRISIS.label) {
                        Spacer(Modifier.height(8.dp))
                        Text("This estimate is very high. Check with a cuff now and contact a clinician if it is confirmed.", color = Pl.red, fontSize = 13.sp)
                    }
                }

                PCard {
                    Text("Tune with a cuff", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Right after an estimate, take a cuff reading and enter it. Three or more readings on different days make the estimate personal.",
                        color = Pl.sub, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(sysTxt, { sysTxt = it.filter(Char::isDigit).take(3) }, label = { Text("Systolic") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(diaTxt, { diaTxt = it.filter(Char::isDigit).take(3) }, label = { Text("Diastolic") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.calibrate(sysTxt.toIntOrNull() ?: 0, diaTxt.toIntOrNull() ?: 0)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Pl.card2, contentColor = Pl.text),
                    ) { Text("Save cuff reading") }
                    if (ui.calMessage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.calMessage, color = Pl.sub, fontSize = 12.sp)
                    }
                }
            }
        }

        if (ui.history.isNotEmpty()) {
            PCard {
                Text("History", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val fmt = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()) }
                ui.history.take(8).forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            java.time.Instant.ofEpochMilli(h.tsMs).atZone(java.time.ZoneId.systemDefault()).format(fmt),
                            color = Pl.sub, fontSize = 13.sp,
                        )
                        Text("${h.sys.roundToInt()}/${h.dia.roundToInt()}", color = catColor(h.category), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
