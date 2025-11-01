package com.example.todotimerapp2.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object TimerScheduler {
    fun schedule(context: Context, todoId: Long, endTimeMillis: Long, title: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            Intent(context, TimerAlarmReceiver::class.java).apply {
                action = "TIMER_ALARM"
                putExtra("todoId", todoId)
                putExtra("title", title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val type = AlarmManager.RTC_WAKEUP
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(type, endTimeMillis, pi)
        } else {
            am.setExact(type, endTimeMillis, pi)
        }
    }

    fun cancel(context: Context, todoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            Intent(context, TimerAlarmReceiver::class.java).apply { action = "TIMER_ALARM" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
