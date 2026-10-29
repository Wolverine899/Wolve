package com.wolve.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.R
import com.wolve.app.analytics.StrainMath
import com.wolve.app.analytics.Zones
import com.wolve.app.data.Live
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** A vessel as normalised (x, y) points from the heart outwards. */
private class Vessel(val pts: List<Pair<Float, Float>>)

private class BodyModel(val image: Int, val ratio: Float, val heart: Pair<Float, Float>, val arteries: List<Vessel>)

private fun v(vararg p: Float): Vessel = Vessel((0 until p.size / 2).map { Pair(p[it * 2], p[it * 2 + 1]) })

private val MALE = BodyModel(
    R.drawable.body_male, 272f / 698f, Pair(0.53f, 0.236f),
    listOf(
        v(0.53f, 0.26f, 0.52f, 0.30f, 0.52f, 0.36f, 0.52f, 0.41f),                                              // aorta
        v(0.52f, 0.41f, 0.43f, 0.45f, 0.395f, 0.50f, 0.37f, 0.61f, 0.36f, 0.716f, 0.31f, 0.81f, 0.295f, 0.90f, 0.25f, 0.95f), // left leg
        v(0.52f, 0.41f, 0.60f, 0.45f, 0.616f, 0.50f, 0.607f, 0.61f, 0.643f, 0.716f, 0.69f, 0.81f, 0.726f, 0.90f),            // right leg
        v(0.50f, 0.21f, 0.45f, 0.17f, 0.44f, 0.11f, 0.43f, 0.06f),                                              // left carotid
        v(0.55f, 0.21f, 0.58f, 0.16f, 0.58f, 0.10f, 0.59f, 0.06f),                                              // right carotid
        v(0.50f, 0.20f, 0.37f, 0.19f, 0.23f, 0.22f, 0.20f, 0.30f, 0.156f, 0.39f, 0.11f, 0.49f, 0.055f, 0.515f),  // left arm
        v(0.56f, 0.20f, 0.65f, 0.19f, 0.77f, 0.215f, 0.81f, 0.29f, 0.86f, 0.39f, 0.94f, 0.49f, 0.98f, 0.515f),  // right arm
    ),
)

private val FEMALE = BodyModel(
    R.drawable.body_female, 198f / 698f, Pair(0.52f, 0.25f),
    listOf(
        v(0.52f, 0.27f, 0.525f, 0.31f, 0.525f, 0.36f, 0.525f, 0.41f),
        v(0.525f, 0.41f, 0.43f, 0.45f, 0.38f, 0.50f, 0.39f, 0.61f, 0.42f, 0.716f, 0.38f, 0.81f, 0.40f, 0.895f, 0.38f, 0.95f),
        v(0.525f, 0.41f, 0.60f, 0.45f, 0.62f, 0.50f, 0.63f, 0.61f, 0.67f, 0.716f, 0.69f, 0.81f, 0.68f, 0.895f, 0.66f, 0.95f),
        v(0.49f, 0.21f, 0.45f, 0.17f, 0.43f, 0.12f, 0.42f, 0.08f),
        v(0.54f, 0.21f, 0.55f, 0.16f, 0.54f, 0.12f, 0.535f, 0.08f),
        v(0.49f, 0.20f, 0.33f, 0.19f, 0.21f, 0.215f, 0.19f, 0.29f, 0.15f, 0.37f, 0.115f, 0.46f, 0.10f, 0.52f),
        v(0.55f, 0.20f, 0.66f, 0.19f, 0.78f, 0.21f, 0.81f, 0.29f, 0.86f, 0.37f, 0.92f, 0.46f, 0.95f, 0.50f),
    ),
)

/** Point at fraction [t] (0..1) along the vessel, in normalised coordinates. */
private fun along(vs: Vessel, t: Float): Pair<Float, Float> {
    val p = vs.pts
    var total = 0f
    val seg = FloatArray(p.size - 1)
    for (i in 0 until p.size - 1) {
        seg[i] = hypot(p[i + 1].first - p[i].first, p[i + 1].second - p[i].second)
        total += seg[i]
    }
    var d = (t.coerceIn(0f, 1f)) * total
    for (i in seg.indices) {
        if (d <= seg[i] || i == seg.size - 1) {
            val f = if (seg[i] == 0f) 0f else (d / seg[i]).coerceIn(0f, 1f)
            return Pair(p[i].first + (p[i + 1].first - p[i].first) * f, p[i].second + (p[i + 1].second - p[i].second) * f)
        }
        d -= seg[i]
    }
    return p.last()
}

@Composable
fun BodyScreen(vm: AppViewModel) {
    val hr by Live.hr.collectAsStateWithLifecycle()
    val connected by Live.connected.collectAsStateWithLifecycle()
    val m by vm.metrics.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val bp by vm.bp.collectAsStateWithLifecycle()
    var female by remember(profile.male) { mutableStateOf(!profile.male) }
    var mode by remember { mutableStateOf(0) } // 0 all, 1 arterial, 2 venous

    val liveHr = rememberUpdatedState(hr)
    var phase by remember { mutableStateOf(0f) }   // heartbeat cycle 0..1
    var flow by remember { mutableStateOf(0f) }    // blood flow position 0..1
    LaunchedEffect(Unit) {
        var last = 0L
        var beat = 0f
        var fl = 0f
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L && !UiPrefs.reduceMotion) {
                    val dt = (now - last) / 1_000_000_000f
                    val bpm = if (liveHr.value > 0) liveHr.value.toFloat() else 60f
                    beat = (beat + dt * bpm / 60f) % 1f
                    fl = (fl + dt * (0.10f + bpm / 220f * 0.30f)) % 1f
                }
                last = now
                phase = beat
                flow = fl
            }
        }
    }

    val body = if (female) FEMALE else MALE
    val maxHr = StrainMath.maxHr(profile.age)
    val zone = if (hr > 0) Zones.zoneOf(hr, maxHr) else 0
    val zoneName = listOf("Resting", "Warm-up", "Fat burn", "Cardio", "Threshold", "Peak")[zone]

    Page {
        Title("Body", "Your heartbeat and circulation, live")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Male", !female) { female = false }
            Pill("Female", female) { female = true }
            Pill("All blood", mode == 0) { mode = 0 }
            Pill("Arteries", mode == 1) { mode = 1 }
            Pill("Veins", mode == 2) { mode = 2 }
        }
        PCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.height(500.dp).aspectRatio(body.ratio)) {
                    Image(painterResource(body.image), contentDescription = "Circulatory system", modifier = Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val hx = body.heart.first * w
                        val hy = body.heart.second * h
                        // two-beat "lub-dub" envelope
                        val s1 = if (phase < 0.14f) sin(PI.toFloat() * phase / 0.14f) else 0f
                        val s2 = if (phase in 0.30f..0.40f) 0.55f * sin(PI.toFloat() * (phase - 0.30f) / 0.10f) else 0f
                        val beat = maxOf(s1, s2)
                        val base = w * 0.075f
                        if (hr > 0) {
                            drawCircle(Pl.red.copy(alpha = 0.10f + 0.30f * beat), base * (1.1f + 0.5f * beat), Offset(hx, hy))
                            val ring = (phase * 1.6f).coerceAtMost(1f)
                            drawCircle(Pl.red.copy(alpha = 0.35f * (1f - ring)), base * (1f + 2.4f * ring), Offset(hx, hy), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        }
                        val showArt = mode != 2
                        val showVein = mode != 1
                        val n = 7
                        body.arteries.forEachIndexed { idx, vs ->
                            val speedShift = if (idx == 0) 1f else 0f
                            for (k in 0 until n) {
                                if (showArt) {
                                    val t = (k.toFloat() / n + flow) % 1f
                                    val (px, py) = along(vs, t)
                                    val pulse = 0.55f + 0.45f * beat
                                    drawCircle(Color(0xFFFF6B6B).copy(alpha = pulse), (2.6f + speedShift).dp.toPx(), Offset(px * w, py * h))
                                }
                                if (showVein && idx > 0) {
                                    val t = 1f - ((k.toFloat() / n + flow * 0.8f) % 1f)
                                    val (px, py) = along(vs, t)
                                    val cx = body.heart.first
                                    val vx = px + (cx - px) * 0.07f
                                    drawCircle(Color(0xFF5AB8FF).copy(alpha = 0.85f), 2.2.dp.toPx(), Offset(vx * w, py * h))
                                }
                            }
                        }
                        if (connected && hr > 0) drawCircle(Color.White.copy(alpha = 0.9f * beat), base * 0.25f, Offset(hx, hy))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (hr > 0) "Pulse follows your real heartbeat from the strap." else "Connect a strap or start the demo to see your heart beat here.",
                color = Pl.sub, fontSize = 12.sp,
            )
        }
        PCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Heart rate", color = Pl.sub, fontSize = 13.sp)
                    BigNumber(if (hr > 0) "$hr" else "--", "bpm", Pl.red, 48)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Zone", color = Pl.sub, fontSize = 13.sp)
                    Text(zoneName, color = zoneColor(zone), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Beat interval", if (hr > 0) "${60000 / hr} ms" else "--")
                Fact("HRV", if (m.rmssd > 0) "${m.rmssd.toInt()} ms" else "--")
                Fact("Pressure est.", bp.latest?.let { "${it.sys.toInt()}/${it.dia.toInt()}" } ?: "--")
            }
            Text("Blood pressure is the latest estimate from the Pressure screen, not a live reading.", color = Pl.sub, fontSize = 11.sp)
        }
    }
}
