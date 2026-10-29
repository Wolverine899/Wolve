package com.wolve.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.data.Graph
import java.util.Locale

@Composable
fun TrendsScreen(vm: AppViewModel) {
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    var days by remember { mutableStateOf(7) }
    val nights = sleep.nights.takeLast(days)
    val strains = Graph.store.loadDayStrains().toSortedMap().values.toList().takeLast(days)

    Page {
        Title("Trends", "Your last $days days")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("7 days", days == 7) { days = 7 }
            Pill("30 days", days == 30) { days = 30 }
        }
        TrendCard("Recovery", nights.map { it.recovery.toFloat() }, "%", Pl.green, 0)
        TrendCard("Heart rate variability", nights.map { it.hrv.toFloat() }, "ms", Pl.violet, 0)
        TrendCard("Resting heart rate", nights.map { it.rhr.toFloat() }, "bpm", Pl.red, 0)
        TrendCard("Sleep", nights.map { it.totalMin / 60f }, "h", Pl.blue, 1)
        TrendCard("Day strain", strains.map { it.toFloat() }, "", Pl.orange, 1)
    }
}

@Composable
private fun TrendCard(title: String, values: List<Float>, unit: String, color: Color, decimals: Int) {
    PCard {
        Text(title, color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (values.size < 2) {
            Text("Needs at least two days of data.", color = Pl.sub, fontSize = 13.sp)
        } else {
            val f = "%.${decimals}f"
            Text(
                "Average ${String.format(Locale.US, f, values.average())} $unit   Low ${String.format(Locale.US, f, values.min())}   High ${String.format(Locale.US, f, values.max())}",
                color = Pl.sub, fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Sparkline(values, color, Modifier.fillMaxWidth().height(70.dp))
        }
    }
}
