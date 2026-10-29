package com.wolve.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.StressMath
import com.wolve.app.data.StressTracker
import java.util.Locale

@Composable
fun StressScreen() {
    val points by StressTracker.today.collectAsStateWithLifecycle()
    val valid = points.filter { it.score >= 0.0 }
    val last = valid.lastOrNull()
    val avg = if (valid.isEmpty()) 0.0 else valid.map { it.score }.average()

    Page {
        Title("Stress Monitor", "Estimated from HRV every two minutes")
        PCard {
            if (last == null) {
                Text("No stress data yet. Keep your strap connected for a few minutes.", color = Pl.sub, fontSize = 14.sp)
            } else {
                Text("Right now", color = Pl.sub, fontSize = 14.sp)
                BigNumber(StressMath.label(last.score), "", stressColor(last.score), 44)
                Text(String.format(Locale.US, "Score %.1f of 3.0, day average %.1f", last.score, avg), color = Pl.sub, fontSize = 13.sp)
            }
        }
        PCard {
            Text("Last 24 hours", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (points.isEmpty()) {
                Text("Nothing recorded.", color = Pl.sub, fontSize = 13.sp)
            } else {
                StressBars(points.takeLast(360), Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Low", color = Pl.green, fontSize = 12.sp)
                    Text("Moderate", color = Pl.amber, fontSize = 12.sp)
                    Text("High", color = Pl.red, fontSize = 12.sp)
                    Text("Exercise", color = Pl.sub, fontSize = 12.sp)
                }
            }
        }
        PCard {
            Text(
                "Wolve compares your HRV with your own daytime baseline, which it learns as you wear the strap. " +
                    "Periods of hard exercise are shown in grey and are not counted as stress. This is an estimate, not a medical measurement.",
                color = Pl.sub, fontSize = 13.sp,
            )
        }
    }
}
