package com.wolve.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wolve.app.MainActivity
import com.wolve.app.R
import com.wolve.app.data.Graph
import com.wolve.app.data.Live
import com.wolve.app.data.SleepRecorder

/** Keeps the Bluetooth connection and overnight recording alive while the screen is off. */
class TrackingService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var firedFor = 0L

    private val smartCheck = object : Runnable {
        override fun run() {
            smartTick()
            handler.postDelayed(this, 30_000L)
        }
    }

    /** Smart alarm: inside the wake window, ring early when heart rate shows you are already close to waking. */
    private fun smartTick() {
        val cfg = Graph.store.loadAlarm()
        if (!cfg.enabled || !cfg.smart || !SleepRecorder.active.value) return
        val now = System.currentTimeMillis()
        val target = AlarmScheduler.nextTrigger(cfg.hour, cfg.minute, now)
        if (target - now > 30L * 60L * 1000L || firedFor == target) return
        val recent = Live.hrSeries.value.filter { it.tsMs >= now - 180_000L }
        if (recent.size < 60) return
        val avg = recent.map { it.bpm }.average()
        if (avg > Graph.store.restHr * 1.10 + 2.0) {
            firedFor = target
            AlarmScheduler.fire(this)
            AlarmScheduler.schedule(this, cfg, target + 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tracking", NotificationManager.IMPORTANCE_LOW))
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wolve:tracking").apply {
            setReferenceCounted(false)
            acquire(10L * 60L * 60L * 1000L)
        }
        handler.postDelayed(smartCheck, 30_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wolve)
            .setContentTitle("Wolve is tracking")
            .setContentText("Heart data stays on this phone")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(smartCheck)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "wolve_tracking"
        private const val NOTIFICATION_ID = 1

        private fun allowed(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        /** Safe to call at any time: does nothing when the platform would refuse a foreground service. */
        fun start(ctx: Context) {
            if (!allowed(ctx)) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
            } catch (e: Exception) {
                // Background-start restrictions: tracking continues while the app is open.
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, TrackingService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
