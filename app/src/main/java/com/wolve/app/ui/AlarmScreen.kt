package com.wolve.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@Composable
fun AlarmScreen(vm: AppViewModel) {
    val cfg by vm.alarm.collectAsStateWithLifecycle()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun total(): Int = cfg.hour * 60 + cfg.minute
    fun setTotal(t: Int) {
        val v = ((t % 1440) + 1440) % 1440
        vm.setAlarm(cfg.copy(hour = v / 60, minute = v % 60))
    }

    Page {
        Title("Smart Alarm", "Wake at your best moment")
        PCard {
            Text(
                String.format(Locale.US, "%02d:%02d", cfg.hour, cfg.minute),
                color = Pl.text, fontSize = 64.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { setTotal(total() - 60) }) { Text("Hour -") }
                OutlinedButton(onClick = { setTotal(total() + 60) }) { Text("Hour +") }
                OutlinedButton(onClick = { setTotal(total() - 5) }) { Text("Min -") }
                OutlinedButton(onClick = { setTotal(total() + 5) }) { Text("Min +") }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Alarm on", color = Pl.text, fontSize = 16.sp)
                Switch(
                    checked = cfg.enabled,
                    onCheckedChange = {
                        if (it && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.setAlarm(cfg.copy(enabled = it))
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pl.accent, checkedThumbColor = Pl.bg),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Smart wake (30 min window)", color = Pl.text, fontSize = 16.sp)
                Switch(
                    checked = cfg.smart,
                    onCheckedChange = { vm.setAlarm(cfg.copy(smart = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pl.accent, checkedThumbColor = Pl.bg),
                )
            }
        }
        PCard {
            Text("How it works", color = Pl.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "The alarm always rings at the set time. With smart wake on and sleep tracking running, it rings up to 30 minutes early " +
                    "when your heart rate rises, which usually means light sleep. It rings on this phone with sound and vibration, " +
                    "not on the strap. Allow notifications so the alarm can show. Alarms are cleared if the phone restarts; open Wolve and toggle it again.",
                color = Pl.sub, fontSize = 13.sp,
            )
        }
    }
}
