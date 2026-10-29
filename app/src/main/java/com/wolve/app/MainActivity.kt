package com.wolve.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wolve.app.ui.ActivityScreen
import com.wolve.app.ui.BodyScreen
import com.wolve.app.ui.DiagnosticsScreen
import com.wolve.app.ui.RhythmScreen
import com.wolve.app.ui.SettingsScreen
import com.wolve.app.ui.UiPrefs
import com.wolve.app.data.WorkoutRecorder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.ui.AiCoachScreen
import com.wolve.app.ui.BreathworkScreen
import com.wolve.app.ui.Logo
import com.wolve.app.ui.WeeklyRecapScreen
import com.wolve.app.ui.AlarmScreen
import com.wolve.app.ui.AppViewModel
import com.wolve.app.ui.AboutScreen
import com.wolve.app.ui.BackBar
import com.wolve.app.ui.BackupScreen
import com.wolve.app.ui.BpScreen
import com.wolve.app.ui.CoachScreen
import com.wolve.app.ui.DevicesScreen
import com.wolve.app.ui.HealthScreen
import com.wolve.app.ui.HeartScreen
import com.wolve.app.ui.HomeScreen
import com.wolve.app.ui.JournalScreen
import com.wolve.app.ui.MoreScreen
import com.wolve.app.ui.Pl
import com.wolve.app.ui.ProfileScreen
import com.wolve.app.ui.SleepScreen
import com.wolve.app.ui.StressScreen
import com.wolve.app.ui.TrendsScreen
import com.wolve.app.ui.WolveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WolveTheme { WolveRoot() } }
    }
}

private val tabs = listOf("Home" to "◔", "Sleep" to "☾", "Activity" to "⚡", "Health" to "♥", "More" to "☰")

@Composable
fun WolveRoot(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var sub by rememberSaveable { mutableStateOf("") }

    // "tab:N" jumps to a bottom tab; any other key opens a sub screen
    val go: (String) -> Unit = { key ->
        if (key.startsWith("tab:")) {
            tab = key.removePrefix("tab:").toIntOrNull() ?: 0
            sub = ""
        } else {
            sub = key
        }
    }

    BackHandler(enabled = sub.isNotEmpty()) { sub = "" }

    val recording by WorkoutRecorder.active.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(recording, UiPrefs.keepScreenOn) {
        view.keepScreenOn = recording && UiPrefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(skyBrush())) {
    UiPrefs.bg?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Pl.card.copy(alpha = UiPrefs.barAlpha)) {
                tabs.forEachIndexed { i, (label, glyph) ->
                    NavigationBarItem(
                        selected = tab == i && sub.isEmpty(),
                        onClick = {
                            tab = i
                            sub = ""
                        },
                        icon = { Text(glyph, fontSize = 18.sp) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Pl.card2,
                            selectedIconColor = Pl.text, selectedTextColor = Pl.text,
                            unselectedIconColor = Pl.sub, unselectedTextColor = Pl.sub,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (sub.isNotEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    BackBar { sub = "" }
                    Box(Modifier.weight(1f)) { SubScreen(sub, vm, go) }
                }
            } else {
                when (tab) {
                    0 -> HomeScreen(vm, go)
                    1 -> SleepScreen(vm)
                    2 -> ActivityScreen(vm)
                    3 -> HealthScreen(vm, go)
                    else -> MoreScreen(go)
                }
            }
            if (sub != "ai" && UiPrefs.coachBubble) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(16.dp).size(58.dp)
                        .clip(CircleShape).background(Pl.card2).border(2.dp, Pl.green, CircleShape)
                        .clickable { sub = "ai" },
                    contentAlignment = Alignment.Center,
                ) { Logo(58.dp) }
            }
        }
    }
    }
}

/** Day-cycle sky (or a plain canvas) behind every screen. */
@Composable
private fun skyBrush(): Brush {
    if (!UiPrefs.sky) return SolidColor(Pl.bg)
    val h = java.time.LocalTime.now().hour
    val dark = UiPrefs.dark
    val (top, bottom) = when {
        h in 5..7 -> if (dark) Pair(0xFF2B2F66, 0xFF8A5A6E) else Pair(0xFFD9DDF5, 0xFFF3D9D2)
        h in 8..16 -> if (dark) Pair(0xFF22406B, 0xFF3C6A94) else Pair(0xFFCFE4F8, 0xFFEEF5FC)
        h in 17..20 -> if (dark) Pair(0xFF2E2760, 0xFF7A4660) else Pair(0xFFDCD3F2, 0xFFF3DCE0)
        else -> if (dark) Pair(0xFF171C4A, 0xFF3B2646) else Pair(0xFFC9D0EA, 0xFFE6D9E8)
    }
    return Brush.verticalGradient(listOf(Color(top), Color(bottom)))
}

@Composable
private fun SubScreen(key: String, vm: AppViewModel, go: (String) -> Unit) {
    when (key) {
        "coach" -> CoachScreen(vm)
        "journal" -> JournalScreen(vm)
        "trends" -> TrendsScreen(vm)
        "stress" -> StressScreen()
        "heart" -> HeartScreen(vm)
        "pressure" -> BpScreen(vm)
        "ai" -> AiCoachScreen(vm)
        "weekly" -> WeeklyRecapScreen(vm)
        "breath" -> BreathworkScreen(vm)
        "alarm" -> AlarmScreen(vm)
        "devices" -> DevicesScreen(vm)
        "profile" -> SettingsScreen(vm, go)
        "body" -> BodyScreen(vm)
        "diagnostics" -> DiagnosticsScreen(vm)
        "rhythm" -> RhythmScreen()
        "backup" -> BackupScreen(vm)
        else -> AboutScreen()
    }
}
