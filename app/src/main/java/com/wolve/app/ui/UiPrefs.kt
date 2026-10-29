package com.wolve.app.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.wolve.app.analytics.StrainMath
import com.wolve.app.data.Store
import java.io.File

/** Appearance and behaviour switches. Reading one in a composable makes the screen update when it changes. */
object UiPrefs {
    var theme by mutableStateOf(0)            // 0 system, 1 light, 2 dark
    var systemDark by mutableStateOf(true)
    var accent by mutableStateOf(0)           // 0 mint, 1 blue, 2 custom
    var accentCustom by mutableStateOf(0xFF3DDC97.toInt())
    var clock by mutableStateOf(0)            // 0 system, 1 12-hour, 2 24-hour
    var softGauge by mutableStateOf(false)
    var reduceMotion by mutableStateOf(false)
    var sky by mutableStateOf(true)
    var transparentCards by mutableStateOf(false)
    var cardTransparency by mutableStateOf(0.5f)
    var barAlpha by mutableStateOf(1f)
    var coachBubble by mutableStateOf(true)
    var effort100 by mutableStateOf(false)
    var imperial by mutableStateOf(false)
    var keepScreenOn by mutableStateOf(true)
    var hydrationOn by mutableStateOf(true)
    var journalReminder by mutableStateOf(true)
    var illnessHeadsUp by mutableStateOf(false)
    var stressCheckins by mutableStateOf(false)
    var shareWithCoach by mutableStateOf(true)
    var rhythmOk by mutableStateOf(false)
    var keepBackground by mutableStateOf(true)
    var bg by mutableStateOf<ImageBitmap?>(null)
    var avatar by mutableStateOf<ImageBitmap?>(null)

    val dark: Boolean get() = theme == 2 || (theme == 0 && systemDark)

    fun load(s: Store, files: File) {
        theme = s.gi("ui_theme", 0)
        accent = s.gi("ui_accent", 0)
        accentCustom = s.gi("ui_accent_custom", 0xFF3DDC97.toInt())
        clock = s.gi("ui_clock", 0)
        softGauge = s.gb("ui_soft", false)
        reduceMotion = s.gb("ui_reduce", false)
        sky = s.gb("ui_sky", true)
        transparentCards = s.gb("ui_tcards", false)
        cardTransparency = s.gf("ui_tcards_a", 0.5f)
        barAlpha = s.gf("ui_bar_a", 1f)
        coachBubble = s.gb("ui_bubble", true)
        effort100 = s.gb("ui_effort100", false)
        imperial = s.gb("ui_imperial", false)
        keepScreenOn = s.gb("ui_keepon", true)
        hydrationOn = s.gb("ui_hydration", true)
        journalReminder = s.gb("ui_jreminder", true)
        illnessHeadsUp = s.gb("ui_illness", false)
        stressCheckins = s.gb("ui_stress", false)
        shareWithCoach = s.gb("ui_share", true)
        rhythmOk = s.gb("ui_rhythm", false)
        keepBackground = s.gb("ui_keepbg", true)
        StrainMath.maxHrOverride = s.gf("max_hr_override", 0f).toDouble()
        bg = readImage(File(files, "bg.jpg"))
        avatar = readImage(File(files, "avatar.jpg"))
    }

    fun readImage(f: File): ImageBitmap? = try {
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
    } catch (e: Throwable) {
        null
    }
}
