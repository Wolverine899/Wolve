package com.wolve.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.BEHAVIORS
import com.wolve.app.analytics.JournalInsights
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun JournalScreen(vm: AppViewModel) {
    val journal by vm.journal.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val today = LocalDate.now().toString()
    val set = journal[today] ?: emptySet()
    val effects = JournalInsights.effects(journal, sleep.nights)

    Page {
        Title("Journal", "Log today. Tomorrow's recovery shows what mattered.")
        PCard {
            BEHAVIORS.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(b.label, color = Pl.text, fontSize = 15.sp)
                    Switch(
                        checked = b.id in set,
                        onCheckedChange = { vm.toggleBehavior(b.id) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pl.accent, checkedThumbColor = Pl.bg),
                    )
                }
            }
        }
        PCard {
            Text("Insights", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (effects.isEmpty()) {
                Text(
                    "Insights appear after at least 3 days with and 3 days without a habit, each followed by a tracked night.",
                    color = Pl.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                effects.take(6).forEach { e ->
                    val color: Color = if (e.delta >= 0) Pl.green else Pl.red
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.label, color = Pl.text, fontSize = 14.sp)
                        Text(
                            String.format(Locale.US, "%+d recovery", e.delta.roundToInt()),
                            color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("${e.withN} days with, ${e.withoutN} without", color = Pl.sub, fontSize = 11.sp)
                }
                Text("Correlation, not proof of cause.", color = Pl.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
