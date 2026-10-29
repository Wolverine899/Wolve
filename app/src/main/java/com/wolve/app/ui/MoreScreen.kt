package com.wolve.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.data.Live

@Composable
fun MoreScreen(go: (String) -> Unit) {
    val status by Live.status.collectAsStateWithLifecycle()
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(64.dp)
            Column(Modifier.padding(start = 8.dp)) {
                Text("WOLVE", color = Pl.text, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                Text(status, color = Pl.sub, fontSize = 13.sp)
            }
        }

        MenuRow("⚙", "Settings", "Profile, appearance, strap, health, icon, backups") { go("profile") }
        SectionLabel("Performance")
        MenuRow("✦", "Wolve AI Coach", "ChatGPT or Gemini: briefing, diet plan, lab reports") { go("ai") }
        MenuRow("▤", "Weekly recap", "Active minutes, workouts and achievements") { go("weekly") }
        MenuRow("◈", "Coach", "Daily strain target and sleep plan") { go("coach") }
        MenuRow("✎", "Journal", "Habits and what they do to your recovery") { go("journal") }
        MenuRow("≋", "Trends", "7 and 30 day charts") { go("trends") }

        SectionLabel("Health")
        MenuRow("❍", "Breathwork", "Guided breathing to lower stress") { go("breath") }
        MenuRow("◐", "Stress monitor", "Stress through the day") { go("stress") }
        MenuRow("☤", "Body", "Live heartbeat and blood circulation") { go("body") }
        MenuRow("♥", "Live heart and ECG", "Real-time heart rate, ECG and HRV") { go("heart") }
        MenuRow("⌁", "Blood pressure estimate", "From heart rate, HRV and ECG") { go("pressure") }

        SectionLabel("Device and settings")
        MenuRow("⏰", "Smart alarm", "Wake in your lightest sleep") { go("alarm") }
        MenuRow("⌬", "Devices", "Connect a strap or start the demo") { go("devices") }
        MenuRow("⌕", "Diagnostics", "Connection log and heart-rate test") { go("diagnostics") }
        MenuRow("⇅", "Backup and restore", "Export or import your data") { go("backup") }
        MenuRow("ⓘ", "About", "Privacy and how the numbers work") { go("about") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Pl.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))
}
