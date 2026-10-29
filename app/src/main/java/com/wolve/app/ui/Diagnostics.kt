package com.wolve.app.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.Hrv
import com.wolve.app.data.Live
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val status by Live.status.collectAsStateWithLifecycle()
    val connected by Live.connected.collectAsStateWithLifecycle()
    val hr by Live.hr.collectAsStateWithLifecycle()
    val lastHr by Live.lastHrAt.collectAsStateWithLifecycle()
    val battery by Live.battery.collectAsStateWithLifecycle()
    val name by Live.deviceName.collectAsStateWithLifecycle()
    val log by Live.diag.collectAsStateWithLifecycle()
    val ageSec = if (lastHr > 0L) (System.currentTimeMillis() - lastHr) / 1000L else -1L
    val rrCount = Live.recentRr(600).size

    Page {
        Title("Diagnostics", "Find out why heart data is or is not arriving")
        PCard {
            Text("Connection", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Fact("Status", status)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Device", if (name.isNotEmpty()) name else "--")
                Fact("Connected", if (connected) "Yes" else "No")
                Fact("Battery", if (battery >= 0) "$battery%" else "--")
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Heart rate", if (hr > 0) "$hr bpm" else "--")
                Fact("Last packet", if (ageSec >= 0) "$ageSec s ago" else "never")
                Fact("Beat intervals", "$rrCount")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (connected && hr == 0) "Connected but silent. On a WHOOP strap turn ON Heart Rate Broadcast in the WHOOP app, close the WHOOP app completely, then reconnect."
                else if (connected) "Heart data is flowing."
                else "Not connected. Open More, Devices, and tap Scan. If your WHOOP does not appear, use Scan all devices.",
                color = if (connected && hr > 0) Pl.green else Pl.amber, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.scan(true) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
                ) { Text("Scan all devices") }
                OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f)) { Text("Disconnect") }
            }
        }
        PCard {
            Text("Log", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (log.isEmpty()) {
                Text("Nothing yet. Scan and connect, then come back.", color = Pl.sub, fontSize = 13.sp)
            } else {
                log.takeLast(60).forEach { Text(it, color = Pl.sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 2.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Wolve strap log").putExtra(Intent.EXTRA_TEXT, log.joinToString("\n"))
                    ctx.startActivity(Intent.createChooser(i, "Share strap log"))
                }, modifier = Modifier.weight(1f)) { Text("Share log") }
                OutlinedButton(onClick = { Live.diag.value = emptyList() }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
    }
}

/** Poincare plot of beat-to-beat intervals with plain regularity statistics. Not an ECG and not a diagnosis. */
@Composable
fun RhythmScreen() {
    val rr = Hrv.clean(Live.recentRr(300))
    var sd1 = 0.0
    var sd2 = 0.0
    var regular = 0
    if (rr.size >= 30) {
        sd1 = Hrv.rmssd(rr) / sqrt(2.0)
        sd2 = sqrt(max(0.0, 2.0 * Hrv.sdnn(rr) * Hrv.sdnn(rr) - sd1 * sd1))
        var close = 0
        for (i in 1 until rr.size) if (kotlin.math.abs(rr[i] - rr[i - 1]) <= 0.10 * rr[i - 1]) close++
        regular = (100.0 * close / (rr.size - 1)).toInt()
    }
    Page {
        Title("Rhythm", "Experimental: your beat-to-beat timing")
        PCard {
            if (rr.size < 30) {
                Text("Needs about a minute of beat intervals from a connected strap. Stay still and check back.", color = Pl.sub, fontSize = 14.sp)
            } else {
                Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                    val lo = rr.minOrNull() ?: 600.0
                    val hi = rr.maxOrNull() ?: 1000.0
                    val pad = max(40.0, (hi - lo) * 0.2)
                    val a = lo - pad
                    val b = hi + pad
                    val side = min(size.width, size.height)
                    drawLine(Pl.card2, Offset(0f, side), Offset(side, 0f), strokeWidth = 1.dp.toPx())
                    for (i in 1 until rr.size) {
                        val x = ((rr[i - 1] - a) / (b - a)).toFloat() * side
                        val y = side - ((rr[i] - a) / (b - a)).toFloat() * side
                        drawCircle(Pl.accent.copy(alpha = 0.7f), 3.dp.toPx(), Offset(x, y))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Fact("Short-term spread", String.format(Locale.US, "%.0f ms", sd1))
                    Fact("Long-term spread", String.format(Locale.US, "%.0f ms", sd2))
                    Fact("Regular beats", "$regular%")
                }
            }
        }
        PCard {
            Text(
                "A tight, comet-shaped cloud usually means steady rhythm; a wide, scattered cloud can come from movement, talking, caffeine or a loose strap. " +
                    "This is a picture of timing, not an ECG, and it cannot detect or rule out any heart condition. See a clinician about palpitations or chest pain.",
                color = Pl.sub, fontSize = 12.sp,
            )
        }
    }
}
