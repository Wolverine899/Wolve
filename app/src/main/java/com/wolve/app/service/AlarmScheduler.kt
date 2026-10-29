package com.wolve.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.wolve.app.MainActivity
import com.wolve.app.R
import com.wolve.app.data.AlarmCfg
import com.wolve.app.data.Graph
import java.util.Calendar

/** Wake-up alarm. Uses the system alarm clock API, so it needs no special exact-alarm permission. */
object AlarmScheduler {
    private const val CHANNEL = "wolve_alarm"
    private const val NOTIFICATION_ID = 2

    /** Next time [hour]:[minute] occurs strictly after [after]. */
    fun nextTrigger(hour: Int, minute: Int, after: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = after
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= after) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun firePending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 11, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(ctx: Context, cfg: AlarmCfg, after: Long = System.currentTimeMillis()) {
        if (!cfg.enabled) {
            cancel(ctx)
            return
        }
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val show = PendingIntent.getActivity(ctx, 12, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val t = nextTrigger(cfg.hour, cfg.minute, after)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), firePending(ctx))
        } catch (e: Exception) {
            // Some devices refuse alarm scheduling in restricted modes; the UI still shows the setting.
        }
    }

    fun cancel(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(firePending(ctx))
        } catch (e: Exception) {
        }
    }

    fun fire(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(CHANNEL, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(
            uri,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        )
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(ctx, 13, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wolve)
            .setContentTitle("Wake up")
            .setContentText("Good morning from Wolve. Tap to open.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(NOTIFICATION_ID, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlarmScheduler.fire(context)
        val cfg = Graph.store.loadAlarm()
        if (cfg.enabled) AlarmScheduler.schedule(context, cfg, System.currentTimeMillis() + 60_000L)
    }
}
