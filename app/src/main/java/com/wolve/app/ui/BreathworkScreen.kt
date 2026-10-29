package com.wolve.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.data.Live
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min

private class BreathPhase(val label: String, val sec: Double, val scale: Float)

private class BreathPattern(val name: String, val note: String, val phases: List<BreathPhase>)

private val PATTERNS = listOf(
    BreathPattern(
        "Box breathing", "4-4-4-4. Steady calm under pressure.",
        listOf(BreathPhase("Inhale", 4.0, 1f), BreathPhase("Hold", 4.0, 1f), BreathPhase("Exhale", 4.0, 0.45f), BreathPhase("Hold", 4.0, 0.45f)),
    ),
    BreathPattern(
        "4-7-8 wind-down", "Slows the body before sleep.",
        listOf(BreathPhase("Inhale", 4.0, 1f), BreathPhase("Hold", 7.0, 1f), BreathPhase("Exhale", 8.0, 0.45f)),
    ),
    BreathPattern(
        "Coherence", "About 5.5 breaths a minute, tuned for HRV.",
        listOf(BreathPhase("Inhale", 5.5, 1f), BreathPhase("Exhale", 5.5, 0.45f)),
    ),
    BreathPattern(
        "Physiological sigh", "Two quick inhales, one long exhale. Fast reset.",
        listOf(BreathPhase("Inhale", 2.5, 0.8f), BreathPhase("Top-up sip", 1.5, 1f), BreathPhase("Long exhale", 6.0, 0.45f)),
    ),
)

@Composable
fun BreathworkScreen(vm: AppViewModel) {
    val hr by Live.hr.collectAsStateWithLifecycle()
    val m by vm.metrics.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var phaseLabel by remember { mutableStateOf("Ready") }
    var secondsLeft by remember { mutableStateOf(0) }
    var cycles by remember { mutableStateOf(0) }
    var elapsedSec by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.45f) }

    LaunchedEffect(running, selected) {
        if (!running) {
            scale.snapTo(0.45f)
            phaseLabel = "Ready"
            secondsLeft = 0
            return@LaunchedEffect
        }
        val pattern = PATTERNS[selected]
        cycles = 0
        elapsedSec = 0
        var elapsedMs = 0
        while (true) {
            for (ph in pattern.phases) {
                phaseLabel = ph.label
                val total = (ph.sec * 1000).toInt()
                launch { scale.animateTo(ph.scale, tween(total, easing = LinearEasing)) }
                var left = total
                while (left > 0) {
                    secondsLeft = ceil(left / 1000.0).toInt()
                    val step = min(250, left)
                    delay(step.toLong())
                    left -= step
                    elapsedMs += step
                    elapsedSec = elapsedMs / 1000
                }
            }
            cycles++
        }
    }

    Page {
        Title("Breathwork", "Guided breathing to calm your nervous system")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PATTERNS.forEachIndexed { i, p -> Pill(p.name, i == selected) { if (!running) selected = i } }
        }
        Text(PATTERNS[selected].note, color = Pl.sub, fontSize = 13.sp)

        PCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    val full = size.minDimension / 2f
                    val r = full * scale.value
                    drawCircle(Pl.card2, full)
                    drawCircle(Pl.green.copy(alpha = 0.18f), r * 1.12f)
                    drawCircle(Pl.green.copy(alpha = 0.45f), r)
                    drawCircle(Pl.green, r, style = Stroke(4.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phaseLabel, color = Pl.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (running) Text("$secondsLeft", color = Pl.text, fontSize = 44.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Cycles", "$cycles")
                Fact("Time", String.format(java.util.Locale.US, "%d:%02d", elapsedSec / 60, elapsedSec % 60))
                Fact("Heart rate", if (hr > 0) "$hr bpm" else "--")
                Fact("HRV", if (m.rmssd > 0) "${m.rmssd.toInt()} ms" else "--")
            }
            Spacer(Modifier.height(12.dp))
            if (!running) {
                Button(
                    onClick = {
                        saved = false
                        running = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
                ) { Text("Begin", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = {
                        if (cycles >= 3) {
                            vm.markBehavior("meditation")
                            saved = true
                        }
                        running = false
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.card2, contentColor = Pl.text),
                ) { Text("Finish", fontWeight = FontWeight.Bold) }
            }
            if (saved) Text("Session logged in your journal.", color = Pl.green, fontSize = 13.sp)
        }
        Text(
            "Connect a strap to watch heart rate and HRV respond. Finishing at least three cycles adds a relaxation entry to today's journal.",
            color = Pl.sub, fontSize = 12.sp,
        )
    }
}
