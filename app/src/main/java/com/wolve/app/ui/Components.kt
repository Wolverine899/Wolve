package com.wolve.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.wolve.app.R
import com.wolve.app.data.StressPoint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Design tokens. Colours follow the theme and accent chosen in Settings. */
object Pl {
    private val d: Boolean get() = UiPrefs.dark
    val bg: Color get() = if (d) Color(0xFF0E1218) else Color(0xFFF3F5F8)
    val card: Color get() = if (d) Color(0xFF171D26) else Color(0xFFFFFFFF)
    val cardFill: Color
        get() = card.copy(alpha = if (UiPrefs.transparentCards) (1f - UiPrefs.cardTransparency).coerceIn(0.12f, 1f) else 1f)
    val card2: Color get() = if (d) Color(0xFF212936) else Color(0xFFE6EAF0)
    val text: Color get() = if (d) Color(0xFFF1F4F8) else Color(0xFF101722)
    val sub: Color get() = if (d) Color(0xFF8D98A8) else Color(0xFF5A6678)
    val accent: Color
        get() = when (UiPrefs.accent) {
            1 -> Color(0xFF4DA3FF)
            2 -> Color(UiPrefs.accentCustom)
            else -> if (d) Color(0xFF3DDC97) else Color(0xFF12A36B)
        }
    val green: Color get() = if (d) Color(0xFF3DDC97) else Color(0xFF12A36B)
    val amber: Color get() = if (d) Color(0xFFFFC145) else Color(0xFFC98A00)
    val orange: Color get() = if (d) Color(0xFFFF8A3D) else Color(0xFFE06A1A)
    val red: Color get() = if (d) Color(0xFFFF4D5E) else Color(0xFFD93049)
    val blue: Color get() = if (d) Color(0xFF4DA3FF) else Color(0xFF2A7FE0)
    val violet: Color get() = if (d) Color(0xFFA98BFF) else Color(0xFF7C5CE0)
    val deep: Color get() = if (d) Color(0xFF5B6CFF) else Color(0xFF3F4FE0)
}

@Composable
fun WolveTheme(content: @Composable () -> Unit) {
    val sys = isSystemInDarkTheme()
    SideEffect { UiPrefs.systemDark = sys }
    val scheme = if (UiPrefs.dark) {
        darkColorScheme(
            primary = Pl.accent, onPrimary = Pl.bg, background = Pl.bg, onBackground = Pl.text,
            surface = Pl.card, onSurface = Pl.text, surfaceVariant = Pl.card2, onSurfaceVariant = Pl.sub, outline = Pl.card2,
        )
    } else {
        lightColorScheme(
            primary = Pl.accent, onPrimary = Pl.card, background = Pl.bg, onBackground = Pl.text,
            surface = Pl.card, onSurface = Pl.text, surfaceVariant = Pl.card2, onSurfaceVariant = Pl.sub, outline = Pl.sub,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun readinessColor(score: Int): Color = when {
    score <= 0 -> Pl.sub
    score >= 67 -> Pl.green
    score >= 34 -> Pl.amber
    else -> Pl.red
}

@Composable
fun PCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Pl.cardFill)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun Ring(
    progress: Float,
    color: Color,
    diameter: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (UiPrefs.reduceMotion) 0 else 900), label = "ring")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val topLeft = Offset(s / 2, s / 2)
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(color.copy(alpha = 0.16f), -90f, 360f, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 0.001f) drawArc(color, -90f, 360f * p, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun BigNumber(value: String, unit: String = "", color: Color = Pl.text, size: Int = 44) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        if (unit.isNotEmpty()) Text(unit, color = Pl.sub, fontSize = (size / 3).coerceAtLeast(12).sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    }
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mn = values.min()
        val mx = values.max()
        val rng = max(mx - mn, 1f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (0.92f - 0.84f * (v - mn) / rng)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun EcgTrace(samples: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (k in 1..3) {
            val y = size.height * k / 4f
            drawLine(Pl.card2, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (samples.size < 2) return@Canvas
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in samples) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val rng = max(mx - mn, 1f)
        val path = Path()
        for (i in samples.indices) {
            val x = size.width * i / (samples.size - 1)
            val y = size.height * (0.92f - 0.84f * (samples[i] - mn) / rng)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Pl.red, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
fun Tile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Pl.cardFill)
            .padding(16.dp)
    ) {
        Text(label, color = Pl.sub, fontSize = 13.sp)
        BigNumber(value, unit, size = 30)
    }
}

@Composable
fun Title(text: String, sub: String = "") {
    Column(Modifier.padding(top = 8.dp)) {
        Text(text, color = Pl.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
        if (sub.isNotEmpty()) Text(sub, color = Pl.sub, fontSize = 14.sp)
    }
}

fun hm(min: Int): String = "${min / 60}h ${min % 60}m"

/** Sleep stages as coloured steps: awake on top, then REM, light, deep. */
@Composable
fun Hypnogram(stages: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = stages.length
        if (n == 0) return@Canvas
        val rowH = size.height / 4f
        val barW = size.width / n
        for (r in 1 until 4) {
            drawLine(Pl.card2, Offset(0f, r * rowH), Offset(size.width, r * rowH), strokeWidth = 1.dp.toPx())
        }
        for (i in 0 until n) {
            val code = stages[i] - '0'
            val row = when (code) { 0 -> 0; 3 -> 1; 1 -> 2; else -> 3 }
            val color = when (code) { 0 -> Pl.amber; 3 -> Pl.violet; 1 -> Pl.blue; else -> Pl.deep }
            drawRect(color, Offset(i * barW, row * rowH + rowH * 0.18f), Size(barW + 0.5f, rowH * 0.64f))
        }
    }
}

/** One bar per night (minutes asleep) with a line at the 8 h sleep need. */
@Composable
fun NightBars(minutes: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (minutes.isEmpty()) return@Canvas
        val maxV = max(600f, (minutes.maxOrNull() ?: 0).toFloat())
        val n = minutes.size
        val gap = 8.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        minutes.forEachIndexed { i, m ->
            val h = size.height * m / maxV
            drawRoundRect(Pl.violet, Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(6.dp.toPx()))
        }
        val y = size.height * (1f - 480f / maxV)
        drawLine(Pl.sub.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

@Composable
fun Logo(size: Dp) {
    Image(painterResource(R.drawable.wolve_logo), contentDescription = "Wolve", modifier = Modifier.size(size))
}

@Composable
fun MenuRow(glyph: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pl.cardFill).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, color = Pl.accent, fontSize = 22.sp, modifier = Modifier.width(38.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Pl.sub, fontSize = 12.sp)
        }
        Text("›", color = Pl.sub, fontSize = 24.sp)
    }
}

@Composable
fun BackBar(onBack: () -> Unit) {
    Text(
        "‹  Back", color = Pl.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp).clickable(onClick = onBack),
    )
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (selected) (if (UiPrefs.dark) Pl.bg else Pl.card) else Pl.text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Pl.accent else Pl.card2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

fun stressColor(score: Double): Color = when {
    score < 0.0 -> Pl.sub
    score < 1.0 -> Pl.green
    score < 2.0 -> Pl.amber
    else -> Pl.red
}

/** One bar per two-minute window: height is the stress level, grey bars are exercise. */
@Composable
fun StressBars(points: List<StressPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val n = points.size
        val gap = 1.dp.toPx()
        val bw = max(1f, (size.width - gap * (n - 1)) / n)
        points.forEachIndexed { i, p ->
            val level = if (p.score < 0.0) 0.25f else (p.score.toFloat() / 3f).coerceIn(0.08f, 1f)
            val h = size.height * level
            drawRect(stressColor(p.score), Offset(i * (bw + gap), size.height - h), Size(bw, h))
        }
    }
}

/** Time in each heart-rate zone as one stacked bar (zones 1 to 5). */
@Composable
fun ZoneBar(zoneSec: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = listOf(Pl.blue, Pl.green, Pl.amber, Pl.orange, Pl.red)
        val total = zoneSec.drop(1).sum().toFloat()
        if (total <= 0f) return@Canvas
        var x = 0f
        for (z in 1..5) {
            val w = size.width * zoneSec[z] / total
            if (w > 0f) drawRect(colors[z - 1], Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
}

fun zoneColor(zone: Int): Color = when (zone) {
    1 -> Pl.blue
    2 -> Pl.green
    3 -> Pl.amber
    4 -> Pl.orange
    5 -> Pl.red
    else -> Pl.sub
}

/** Value against your usual range: the band is mean plus or minus one standard deviation. */
@Composable
fun RangeBar(value: Double, mean: Double, sd: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val lo = mean - 3 * sd
        val hi = mean + 3 * sd
        fun x(v: Double) = (size.width * ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)).toFloat()
        val cy = size.height / 2f
        drawLine(Pl.card2, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Pl.green.copy(alpha = 0.5f), Offset(x(mean - sd), cy), Offset(x(mean + sd), cy), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        val inside = value >= mean - sd && value <= mean + sd
        drawCircle(if (inside) Pl.green else Pl.amber, 7.dp.toPx(), Offset(x(value), cy))
    }
}

/** Heart-rate zones 1 to 5 as a spectrum with a marker at the current heart rate (50% to 100% of max). */
@Composable
fun ZoneSpectrum(hr: Int, maxHr: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = listOf(Pl.blue, Pl.green, Pl.amber, Pl.orange, Pl.red)
        val segW = size.width / 5f
        for (i in 0 until 5) {
            drawRect(colors[i].copy(alpha = 0.85f), Offset(i * segW, size.height * 0.3f), Size(segW - 2.dp.toPx(), size.height * 0.4f))
        }
        if (hr > 0) {
            val frac = ((hr / maxHr - 0.5) / 0.5).coerceIn(0.0, 1.0).toFloat()
            drawCircle(Pl.text, size.height * 0.28f, Offset(size.width * frac, size.height / 2f))
        }
    }
}

object Fmt {
    fun time(ts: Long): String {
        val z = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
        val pattern = when (UiPrefs.clock) {
            1 -> "h:mm a"
            2 -> "HH:mm"
            else -> if (android.text.format.DateFormat.is24HourFormat(com.wolve.app.data.Graph.appContext)) "HH:mm" else "h:mm a"
        }
        return z.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
    }

    fun effort(v21: Double): String =
        if (UiPrefs.effort100) String.format(java.util.Locale.US, "%.0f", v21 * 100.0 / 21.0) else String.format(java.util.Locale.US, "%.1f", v21)

    fun weight(kg: Double): String =
        if (UiPrefs.imperial) String.format(java.util.Locale.US, "%.1f", kg * 2.20462) else String.format(java.util.Locale.US, "%.1f", kg)

    fun weightUnit(): String = if (UiPrefs.imperial) "lb" else "kg"
}
