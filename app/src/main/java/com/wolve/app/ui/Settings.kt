package com.wolve.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.data.Graph
import com.wolve.app.data.Live
import com.wolve.app.service.AutoBackup

@Composable
fun DevicesScreen(vm: AppViewModel) {
    val devices by Live.devices.collectAsStateWithLifecycle()
    val status by Live.status.collectAsStateWithLifecycle()
    val connected by Live.connected.collectAsStateWithLifecycle()
    val demo by vm.demoRunning.collectAsStateWithLifecycle()

    val btPerms: Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val request: Array<String> = if (Build.VERSION.SDK_INT >= 33) btPerms + Manifest.permission.POST_NOTIFICATIONS else btPerms
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (btPerms.all { res[it] == true }) vm.scan() else Live.status.value = "Bluetooth permission is needed to scan"
    }

    Page {
        Title("Devices", status)
        PCard {
            Text("Heart-rate devices", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Chest straps and bands that broadcast standard Bluetooth heart rate with R-R intervals. ECG-capable straps such as Polar H10 also stream ECG. " +
                    "A WHOOP strap works when Heart Rate Broadcast is switched on in the WHOOP app.",
                color = Pl.sub, fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { launcher.launch(request) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
                ) { Text("Scan", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f), enabled = connected) { Text("Disconnect") }
            }
            devices.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = Pl.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${d.address}   signal ${d.rssi} dBm", color = Pl.sub, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { vm.connect(d.address) },
                        colors = ButtonDefaults.buttonColors(containerColor = Pl.card2, contentColor = Pl.text),
                    ) { Text("Connect") }
                }
            }
        }
        PCard {
            Text("Demo mode", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Generates a synthetic heart rhythm and ECG so you can explore every screen without hardware.", color = Pl.sub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (demo) vm.stopDemo() else vm.startDemo() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Pl.blue, contentColor = Pl.bg),
            ) { Text(if (demo) "Stop demo" else "Start demo", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var ageTxt by remember(profile.age) { mutableStateOf(profile.age.toString()) }
    var weightTxt by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toInt().toString()) }
    var heightTxt by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toInt().toString()) }
    var zoneAlerts by remember { mutableStateOf(Graph.store.zoneAlerts) }
    var autoWorkout by remember { mutableStateOf(Graph.store.autoWorkout) }
    var male by remember(profile.male) { mutableStateOf(profile.male) }
    var msg by remember { mutableStateOf("") }

    Page {
        Title("Profile", "Used for heart-rate zones, strain and calories")
        PCard {
            OutlinedTextField(
                ageTxt, { ageTxt = it.filter(Char::isDigit).take(2) }, label = { Text("Age") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                weightTxt, { weightTxt = it.filter(Char::isDigit).take(3) }, label = { Text("Weight (kg)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                heightTxt, { heightTxt = it.filter(Char::isDigit).take(3) }, label = { Text("Height (cm)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Male", male) { male = true }
                Pill("Female", !male) { male = false }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val ok = vm.saveProfile(ageTxt.toIntOrNull() ?: 0, male, (weightTxt.toIntOrNull() ?: 0).toDouble(), (heightTxt.toIntOrNull() ?: 0).toDouble())
                    msg = if (ok) "Profile saved." else "Enter age 14 to 90, weight 30 to 250 kg and height 120 to 230 cm."
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
            ) { Text("Save profile", fontWeight = FontWeight.Bold) }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg, color = Pl.sub, fontSize = 12.sp)
            }
        }
        PCard {
            Text("Alerts and automation", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Heart-rate zone alerts", color = Pl.text, fontSize = 15.sp)
                Switch(
                    checked = zoneAlerts,
                    onCheckedChange = { zoneAlerts = it; vm.setZoneAlerts(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pl.accent, checkedThumbColor = Pl.bg),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-detect workouts", color = Pl.text, fontSize = 15.sp)
                Switch(
                    checked = autoWorkout,
                    onCheckedChange = { autoWorkout = it; vm.setAutoWorkout(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pl.accent, checkedThumbColor = Pl.bg),
                )
            }
            Text(
                "Auto-detect records a workout when your heart rate stays above 65% of max for 5 minutes and saves it when you settle. Allow notifications to see alerts.",
                color = Pl.sub, fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun BackupScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            msg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportZip()) }
                "Backup saved."
            } catch (e: Exception) {
                "Could not write the file."
            }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            msg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(com.wolve.app.data.BackupZip.buildCsv(Graph.store)) }
                "CSV export saved."
            } catch (e: Exception) {
                "Could not write the file."
            }
        }
    }
    var autoOn by remember { mutableStateOf(AutoBackup.enabled()) }
    var autoLast by remember { mutableStateOf(Graph.store.gs("auto_backup_last", "")) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                Graph.store.ss("auto_backup_uri", uri.toString())
                Graph.store.sb("auto_backup", true)
                autoOn = true
                AutoBackup.schedule(ctx)
                msg = "Automatic backups set. Wolve saves a dated ZIP every night around 1 am and keeps the newest 7."
            } catch (e: Exception) {
                msg = "That folder cannot be used. Pick another."
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            msg = try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (vm.importBackup(bytes)) "Backup restored." else "That file is not a Wolve backup."
            } catch (e: Exception) {
                "Could not read the file."
            }
        }
    }

    Page {
        Title("Backup and restore", "Your data never leaves the phone unless you export it")
        PCard {
            Text("Export", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Saves a ZIP with Excel workbooks (cycles, sleeps, workouts, journal, blood pressure) plus a restore file. API keys are never included. The ZIP is not encrypted.", color = Pl.sub, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { export.launch("wolve-backup.zip") }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
            ) { Text("Export backup", fontWeight = FontWeight.Bold) }
        }
        PCard {
            Text("Export CSV", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("A ZIP of plain CSV files (days, sleeps, workouts, journal, blood pressure) for spreadsheets and other apps.", color = Pl.sub, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { exportCsv.launch("wolve-csv.zip") }, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
        }
        PCard {
            Text("Automatic backups", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Save a dated backup to a folder every night around 1 am and keep the last 7. Point the folder at Drive or Dropbox for off-device copies. Off until you switch it on.", color = Pl.sub, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
            ) { Text(if (autoOn) "Change backup folder" else "Set up automatic backups", fontWeight = FontWeight.Bold) }
            if (autoOn) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        val ok = AutoBackup.runNow(ctx)
                        autoLast = Graph.store.gs("auto_backup_last", "")
                        msg = if (ok) "Backup saved." else "Could not write to the folder."
                    }, modifier = Modifier.weight(1f)) { Text("Back up now") }
                    OutlinedButton(onClick = {
                        Graph.store.sb("auto_backup", false)
                        autoOn = false
                        AutoBackup.cancel(ctx)
                    }, modifier = Modifier.weight(1f)) { Text("Turn off") }
                }
                if (autoLast.isNotEmpty()) Text("Last backup: $autoLast", color = Pl.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        PCard {
            Text("Restore", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Loads a Wolve backup ZIP and replaces the matching data on this phone. Your previous data is kept in a side file first.", color = Pl.sub, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose backup file") }
        }
        PCard {
            Text("Delete everything", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (!confirm) {
                OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all local data") }
            } else {
                Text("This removes nights, activities, journal, baselines, estimates and cuff readings.", color = Pl.red, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.clearData(); confirm = false; msg = "All local data deleted." },
                        colors = ButtonDefaults.buttonColors(containerColor = Pl.red, contentColor = Pl.bg),
                        modifier = Modifier.weight(1f),
                    ) { Text("Delete") }
                    OutlinedButton(onClick = { confirm = false }, modifier = Modifier.weight(1f)) { Text("Keep") }
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = Pl.amber, fontSize = 13.sp)
    }
}

@Composable
fun AboutScreen() {
    Page {
        Title("About Wolve", "Version 2.0.0")
        PCard {
            Text("Privacy", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Everything stays on this phone. The only network use is the optional AI Coach: when you press send it sends your message and a short summary of your numbers to the provider you chose.", color = Pl.sub, fontSize = 13.sp)
        }
        PCard {
            Text("About the numbers", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Heart rate, R-R intervals, HRV and ECG come directly from your strap, and chest-strap data is very accurate. " +
                    "Recovery, strain, stress and sleep stages are Wolve's own models, not WHOOP's proprietary algorithms, so their values will differ from WHOOP's. " +
                    "Blood pressure is an estimate and is not a medical device.",
                color = Pl.sub, fontSize = 13.sp,
            )
        }
        PCard {
            Text("Compatible hardware", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Any Bluetooth strap with the standard Heart Rate Service. ECG needs a Polar H10 class strap. 64-bit (arm64) and 32-bit phones are both supported.", color = Pl.sub, fontSize = 13.sp)
        }
    }
}
