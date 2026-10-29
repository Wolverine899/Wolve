package com.wolve.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.JournalInsights
import com.wolve.app.analytics.StrainMath
import com.wolve.app.data.Graph
import com.wolve.app.data.Live
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.max

// ------------------------------------------------------------------ small building blocks

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    PCard {
        Text("SETTINGS", color = Pl.sub, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Pl.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Pl.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Pl.text, fontSize = 16.sp)
            if (desc.isNotEmpty()) Text(desc, color = Pl.sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pl.accent, checkedThumbColor = Pl.bg))
    }
}

@Composable
private fun SegRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Pl.text, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> Pill(o, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun StepRow(label: String, value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Pl.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Pl.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(unit, color = Pl.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
        OutlinedButton(onClick = onMinus) { Text("-") }
        Spacer(Modifier.size(6.dp))
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

private fun saveImage(ctx: Context, uri: Uri, target: File, maxSide: Int): Boolean {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val k = maxSide.toFloat() / max(bmp.width, bmp.height)
        val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return true
    } catch (e: Throwable) {
        return false
    }
}

private fun setIcon(ctx: Context, blue: Boolean) {
    try {
        val pm = ctx.packageManager
        val a = ComponentName(ctx, "com.wolve.app.IconTitanium")
        val b = ComponentName(ctx, "com.wolve.app.IconBlue")
        pm.setComponentEnabledSetting(if (blue) b else a, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(if (blue) a else b, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    } catch (e: Exception) {
    }
}

private fun streak(vm: AppViewModel, nights: List<com.wolve.app.analytics.SleepNight>, workoutDates: Set<LocalDate>, journalDates: Set<LocalDate>): Pair<Int, Int> {
    val days = HashSet<LocalDate>()
    nights.forEach { days.add(JournalInsights.dateOf(it.wakeTs)) }
    days.addAll(workoutDates)
    days.addAll(journalDates)
    if (days.isEmpty()) return Pair(0, 0)
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in days.sorted()) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        if (run > longest) longest = run
        prev = d
    }
    var cur = 0
    var d = LocalDate.now()
    if (!days.contains(d)) d = d.minusDays(1)
    while (days.contains(d)) {
        cur++
        d = d.minusDays(1)
    }
    return Pair(cur, longest)
}

// ------------------------------------------------------------------ the screen

@Composable
fun SettingsScreen(vm: AppViewModel, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    val profile by vm.profile.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()
    val connected by Live.connected.collectAsStateWithLifecycle()
    val battery by Live.battery.collectAsStateWithLifecycle()
    val status by Live.status.collectAsStateWithLifecycle()
    val deviceName by Live.deviceName.collectAsStateWithLifecycle()

    var sex by remember(profile.male) { mutableStateOf(store.sex) }
    var maxHrCustom by remember { mutableStateOf(store.gf("max_hr_override", 0f) > 0f) }
    var maxHrValue by remember { mutableStateOf(if (store.gf("max_hr_override", 0f) > 0f) store.gf("max_hr_override", 0f).toInt() else StrainMath.maxHr(profile.age).toInt()) }
    var iconBlue by remember { mutableStateOf(store.gi("ui_icon", 0) == 1) }
    var hrvDeep by remember { mutableStateOf(store.gb("hrv_deep", false)) }
    var autoWorkout by remember { mutableStateOf(store.autoWorkout) }
    var zoneAlerts by remember { mutableStateOf(store.zoneAlerts) }
    var accentHex by remember { mutableStateOf(String.format("%06X", UiPrefs.accentCustom and 0xFFFFFF)) }
    var showStrapInfo by remember { mutableStateOf(false) }
    var recalDone by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = File(ctx.filesDir, "avatar.jpg")
            if (saveImage(ctx, uri, f, 512)) UiPrefs.avatar = UiPrefs.readImage(f) else msg = "Could not read that image."
        }
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = File(ctx.filesDir, "bg.jpg")
            if (saveImage(ctx, uri, f, 1600)) UiPrefs.bg = UiPrefs.readImage(f) else msg = "Could not read that image."
        }
    }

    val (cur, longest) = streak(
        vm, sleep.nights,
        workouts.map { JournalInsights.dateOf(it.startTs) }.toSet(),
        journal.keys.mapNotNull { try { LocalDate.parse(it) } catch (e: Exception) { null } }.toSet(),
    )

    Page {
        Title("Settings", "Your numbers, your strap, and how Wolve works. All on this phone.")

        PCard {
            Text("Streak", color = Pl.text, fontSize = 15.sp)
            Text(if (cur > 0) "$cur days in a row" else "No streak yet", color = Pl.green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Longest: $longest days", color = Pl.sub, fontSize = 13.sp)
        }

        Section("Profile photo") {
            Note("Optional. The avatar shows at the top of Today. Stored only on this phone and never uploaded.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val av = UiPrefs.avatar
                if (av != null) {
                    Image(av, null, Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Logo(64.dp)
                }
                OutlinedButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Choose photo") }
                if (av != null) OutlinedButton(onClick = { File(ctx.filesDir, "avatar.jpg").delete(); UiPrefs.avatar = null }) { Text("Remove") }
            }
        }

        Section("Profile") {
            Note("These power your heart-rate zones, calorie estimates, nutrition targets and recovery baselines. Keep them accurate.")
            StepRow("Age", "${profile.age}", "", { vm.saveProfile((profile.age - 1).coerceAtLeast(14), profile.male, profile.weightKg, profile.heightCm) },
                { vm.saveProfile((profile.age + 1).coerceAtMost(90), profile.male, profile.weightKg, profile.heightCm) })
            SegRow("Sex", listOf("Male", "Female", "Non-binary"), sex) { sex = it; vm.setSex(it) }
            StepRow("Weight", Fmt.weight(profile.weightKg), Fmt.weightUnit(),
                { vm.saveProfile(profile.age, profile.male, (profile.weightKg - 0.5).coerceAtLeast(30.0), profile.heightCm) },
                { vm.saveProfile(profile.age, profile.male, (profile.weightKg + 0.5).coerceAtMost(250.0), profile.heightCm) })
            StepRow("Height", if (UiPrefs.imperial) "${(profile.heightCm / 2.54).toInt()}" else "${profile.heightCm.toInt()}", if (UiPrefs.imperial) "in" else "cm",
                { vm.saveProfile(profile.age, profile.male, profile.weightKg, (profile.heightCm - 1).coerceAtLeast(120.0)) },
                { vm.saveProfile(profile.age, profile.male, profile.weightKg, (profile.heightCm + 1).coerceAtMost(230.0)) })
            ToggleRow("Custom max heart rate", if (maxHrCustom) "Using your own value" else "Auto: ${StrainMath.maxHr(profile.age).toInt()} bpm (Tanaka)", maxHrCustom) {
                maxHrCustom = it
                if (it) {
                    store.sf("max_hr_override", maxHrValue.toFloat())
                    StrainMath.maxHrOverride = maxHrValue.toDouble()
                } else {
                    store.sf("max_hr_override", 0f)
                    StrainMath.maxHrOverride = 0.0
                }
            }
            if (maxHrCustom) {
                StepRow("Max heart rate", "$maxHrValue", "bpm",
                    { maxHrValue = (maxHrValue - 1).coerceAtLeast(120); store.sf("max_hr_override", maxHrValue.toFloat()); StrainMath.maxHrOverride = maxHrValue.toDouble() },
                    { maxHrValue = (maxHrValue + 1).coerceAtMost(220); store.sf("max_hr_override", maxHrValue.toFloat()); StrainMath.maxHrOverride = maxHrValue.toDouble() })
            }
        }

        Section("Units") {
            Note("Your data is always stored the same way. These settings only change how it is shown.")
            SegRow("Body measurements", listOf("Metric", "Imperial"), if (UiPrefs.imperial) 1 else 0) { UiPrefs.imperial = it == 1; store.sb("ui_imperial", it == 1) }
            SegRow("Effort scale", listOf("0-21", "0-100"), if (UiPrefs.effort100) 1 else 0) { UiPrefs.effort100 = it == 1; store.sb("ui_effort100", it == 1) }
        }

        Section("Appearance") {
            SegRow("Theme", listOf("System default", "Light", "Dark"), UiPrefs.theme) { UiPrefs.theme = it; store.si("ui_theme", it) }
            SegRow("Clock", listOf("System default", "12-hour", "24-hour"), UiPrefs.clock) { UiPrefs.clock = it; store.si("ui_clock", it) }
            SegRow("Gauge numbers", listOf("Bold", "Softer"), if (UiPrefs.softGauge) 1 else 0) { UiPrefs.softGauge = it == 1; store.sb("ui_soft", it == 1) }
            SegRow("Accent", listOf("Mint", "Blue", "Custom"), UiPrefs.accent) { UiPrefs.accent = it; store.si("ui_accent", it) }
            if (UiPrefs.accent == 2) {
                OutlinedTextField(
                    accentHex, {
                        accentHex = it.filter { c -> c.isDigit() || c.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                        if (accentHex.length == 6) {
                            val v = (0xFF000000L or accentHex.toLong(16)).toInt()
                            UiPrefs.accentCustom = v
                            store.si("ui_accent_custom", v)
                        }
                    },
                    label = { Text("Accent colour (hex, e.g. 3DDC97)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Section("Display") {
            ToggleRow("Reduce motion", "Holds animated gauges still. Saves a little battery.", UiPrefs.reduceMotion) { UiPrefs.reduceMotion = it; store.sb("ui_reduce", it) }
            ToggleRow("Day-cycle background", "A soft sunrise, day, dusk and night scene behind every screen. Off gives a plain canvas.", UiPrefs.sky) { UiPrefs.sky = it; store.sb("ui_sky", it) }
            ToggleRow("Transparent cards", "Let the background show through cards.", UiPrefs.transparentCards) { UiPrefs.transparentCards = it; store.sb("ui_tcards", it) }
            if (UiPrefs.transparentCards) {
                Text("Card transparency ${(UiPrefs.cardTransparency * 100).toInt()}%", color = Pl.text, fontSize = 15.sp)
                Slider(
                    value = UiPrefs.cardTransparency, onValueChange = { UiPrefs.cardTransparency = it }, onValueChangeFinished = { store.sf("ui_tcards_a", UiPrefs.cardTransparency) },
                    valueRange = 0f..0.85f, colors = SliderDefaults.colors(thumbColor = Pl.accent, activeTrackColor = Pl.accent),
                )
            }
            Text("Background image", color = Pl.text, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
            Note("Optional. Your own photo behind every screen, stored only on this phone.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { bgPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Choose image") }
                if (UiPrefs.bg != null) OutlinedButton(onClick = { File(ctx.filesDir, "bg.jpg").delete(); UiPrefs.bg = null }, modifier = Modifier.weight(1f)) { Text("Remove") }
            }
        }

        Section("Bottom bar") {
            ToggleRow("AI Coach button", "The floating Wolve button at the bottom left.", UiPrefs.coachBubble) { UiPrefs.coachBubble = it; store.sb("ui_bubble", it) }
            Text("Bar transparency", color = Pl.text, fontSize = 16.sp)
            Slider(
                value = 1f - UiPrefs.barAlpha, onValueChange = { UiPrefs.barAlpha = 1f - it }, onValueChangeFinished = { store.sf("ui_bar_a", UiPrefs.barAlpha) },
                valueRange = 0f..0.8f, colors = SliderDefaults.colors(thumbColor = Pl.accent, activeTrackColor = Pl.accent),
            )
        }

        Section("App icon") {
            Note("Choose how Wolve looks on your home screen. The launcher may take a moment to refresh.")
            SegRow("Icon", listOf("Titanium", "Blue Titanium"), if (iconBlue) 1 else 0) {
                iconBlue = it == 1
                store.si("ui_icon", it)
                setIcon(ctx, iconBlue)
            }
        }

        Section("Strap") {
            Note("Wolve reads your strap over standard Bluetooth heart rate.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (connected) "Bonded · streaming" else "Not connected", connected) { }
                if (battery >= 0) Pill("Battery $battery%", false) { }
            }
            Text(
                if (connected) "${if (deviceName.isNotEmpty()) deviceName else "Strap"} is paired and sending data." else status,
                color = Pl.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { go("devices") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg)) { Text("Re-scan") }
                OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f), enabled = connected) { Text("Disconnect") }
            }
            ToggleRow("Keep connected in the background", "Keeps streaming with an ongoing notification, even after you close Wolve. Turn off to disconnect when the app is closed.", UiPrefs.keepBackground) {
                UiPrefs.keepBackground = it
                store.sb("ui_keepbg", it)
            }
            SegRow("HRV window", listOf("Whole night", "Deep sleep"), if (hrvDeep) 1 else 0) { hrvDeep = it == 1; store.sb("hrv_deep", it == 1) }
            Note("Whole night is the default. Deep sleep uses only deep-sleep blocks, which reads higher and steadier. It applies from your next tracked night.")
            OutlinedButton(onClick = {
                val text = Live.diag.value.joinToString("\n").ifEmpty { "No connection events yet." }
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Wolve strap log").putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(i, "Share strap log"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Share strap log (for bug reports)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showStrapInfo = !showStrapInfo }, modifier = Modifier.fillMaxWidth()) { Text("WHOOP 4.0 vs 5.0/MG") }
            if (showStrapInfo) {
                Note(
                    "WHOOP straps share heart rate over standard Bluetooth only while Heart Rate Broadcast is on in the WHOOP app, and only one app can hold the connection at a time. " +
                        "That gives live heart rate and, on most straps, beat intervals for HRV. Wolve does not use WHOOP's private protocol, so battery history, skin temperature, blood oxygen and stored-history sync are not available.",
                )
            }
        }

        Section("Health & wellness") {
            Note("Optional, on-device signals computed only on this phone from data you already have. Never a medical diagnosis.")
            ToggleRow("Illness heads-up", "Watches your resting heart rate and HRV for the pattern that often shows up before you feel unwell, and shows a gentle note on Today.", UiPrefs.illnessHeadsUp) { UiPrefs.illnessHeadsUp = it; store.sb("ui_illness", it) }
            ToggleRow("Hydration tracking", "Shows a fluid log with a daily goal that adjusts to your effort.", UiPrefs.hydrationOn) { UiPrefs.hydrationOn = it; store.sb("ui_hydration", it) }
            ToggleRow("Auto-detect workouts", "Records a workout when your heart rate stays raised, and saves it when you settle.", autoWorkout) { autoWorkout = it; vm.setAutoWorkout(it) }
            ToggleRow("Heart-rate zone alerts", "A notification when your heart rate settles into a new zone.", zoneAlerts) { zoneAlerts = it; vm.setZoneAlerts(it) }
            ToggleRow("Journal reminder", "Show a Today card reminding you to log your journal in the evening.", UiPrefs.journalReminder) { UiPrefs.journalReminder = it; store.sb("ui_jreminder", it) }
            ToggleRow("Keep screen on during a workout", "Holds the screen awake only while you are recording, so live heart rate stays visible. Uses a bit more battery.", UiPrefs.keepScreenOn) { UiPrefs.keepScreenOn = it; store.sb("ui_keepon", it) }
            ToggleRow("Stress check-ins", "Notices a fresh HRV dip while you are still and offers a minute to breathe. Stress here is an autonomic proxy from your own baseline.", UiPrefs.stressCheckins) { UiPrefs.stressCheckins = it; store.sb("ui_stress", it) }
            ToggleRow("Share on-device signals with the Coach", "When you use the AI Coach, also include a short summary of your strongest habit patterns. Off keeps them private.", UiPrefs.shareWithCoach) { UiPrefs.shareWithCoach = it; store.sb("ui_share", it) }
            ToggleRow("Rhythm (experimental)", "A picture of your beat-to-beat timing: a Poincare scatter and plain regularity stats. Not an ECG and not a diagnosis.", UiPrefs.rhythmOk) {
                UiPrefs.rhythmOk = it
                store.sb("ui_rhythm", it)
            }
            if (UiPrefs.rhythmOk) OutlinedButton(onClick = { go("rhythm") }, modifier = Modifier.fillMaxWidth()) { Text("Open Rhythm") }
        }

        Section("Recovery") {
            Note("Recovery is your daily readiness score, learned from your own HRV, resting heart rate and breathing over about four nights. Your history stays.")
            OutlinedButton(onClick = { vm.recalibrate(); recalDone = true }, modifier = Modifier.fillMaxWidth()) { Text("Recalibrate recovery baseline") }
            if (recalDone) Note("Baseline restarted. It rebuilds over the next few tracked nights.")
        }

        Section("Tools") {
            MenuRow("⌕", "Diagnostics", "Connection log, services and heart-rate test") { go("diagnostics") }
            Spacer(Modifier.height(8.dp))
            MenuRow("⇅", "Backup and restore", "ZIP with Excel files, CSV export, automatic backups") { go("backup") }
            Spacer(Modifier.height(8.dp))
            MenuRow("⏰", "Smart alarm", "Wake in your lightest sleep") { go("alarm") }
        }

        Section("About") {
            Text("Wolve: your data, on your phone.", color = Pl.text, fontSize = 16.sp)
            Note("Wolve 2.1. Heart rate, R-R intervals and HRV come from your strap. Recovery, effort, stress and sleep stages are Wolve's own models, not WHOOP's.")
        }
        if (msg.isNotEmpty()) Text(msg, color = Pl.amber, fontSize = 13.sp)
    }
}
