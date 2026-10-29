package com.wolve.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.wolve.app.R
import com.wolve.app.analytics.StrainMath
import com.wolve.app.analytics.Zones
import com.wolve.app.data.Graph
import com.wolve.app.data.WorkoutRecorder

fun notifyUser(id: Int, channel: String, title: String, text: String) {
    try {
        val ctx = Graph.appContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channel, "Heart-rate alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_wolve)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    } catch (e: Exception) {
        // notifications may be blocked by the user; ignore
    }
}

/** Alerts when your heart rate settles in a new zone (needs 20 seconds in the zone, at most one alert a minute). */
object ZoneNotifier {
    private val names = listOf("Resting", "Warm-up", "Fat burn", "Cardio", "Threshold", "Peak")
    private var lastZone = -1
    private var candidate = -1
    private var candidateSince = 0L
    private var lastAlert = 0L

    @Synchronized
    fun onHr(ts: Long, bpm: Int) {
        val s = Graph.store
        if (!s.zoneAlerts) return
        val z = Zones.zoneOf(bpm, StrainMath.maxHr(s.age))
        if (z != candidate) {
            candidate = z
            candidateSince = ts
            return
        }
        if (ts - candidateSince < 20_000L || z == lastZone) return
        val first = lastZone == -1
        lastZone = z
        if (first || ts - lastAlert < 60_000L) return
        lastAlert = ts
        notifyUser(3, "wolve_zones", "Zone ${if (z == 0) "rest" else z.toString()}: ${names[z]}", "Heart rate $bpm bpm")
    }
}

/** Detects sustained hard effort from heart rate alone and records it as a workout. */
object AutoWorkout {
    private var highSince = 0L
    private var lowSince = 0L
    private var running = false

    @Synchronized
    fun onHr(ts: Long, bpm: Int) {
        val s = Graph.store
        if (!s.autoWorkout) return
        if (running && !WorkoutRecorder.active.value) {
            running = false
            highSince = 0L
        }
        if (WorkoutRecorder.active.value && !running) return
        val p = bpm / StrainMath.maxHr(s.age)
        if (!running) {
            if (p >= 0.65) {
                if (highSince == 0L) highSince = ts
                if (ts - highSince >= 5L * 60_000L) {
                    running = true
                    lowSince = 0L
                    WorkoutRecorder.start("Auto-detected", ts, s.age, s.weightKg, s.male, s.restHr)
                    notifyUser(4, "wolve_zones", "Workout detected", "Wolve is recording your heart rate.")
                }
            } else {
                highSince = 0L
            }
        } else if (p < 0.55) {
            if (lowSince == 0L) lowSince = ts
            if (ts - lowSince >= 3L * 60_000L) finish(ts)
        } else {
            lowSince = 0L
        }
    }

    private fun finish(ts: Long) {
        running = false
        highSince = 0L
        lowSince = 0L
        val w = WorkoutRecorder.stop(ts)
        val s = Graph.store
        if (w != null && w.endTs - w.startTs >= 8L * 60_000L) {
            s.saveWorkouts((s.loadWorkouts() + w).takeLast(200))
            Graph.workoutsVersion.value = Graph.workoutsVersion.value + 1
            notifyUser(4, "wolve_zones", "Workout saved", "${(w.endTs - w.startTs) / 60000L} min, strain ${String.format(java.util.Locale.US, "%.1f", w.strain)}")
        }
    }
}
