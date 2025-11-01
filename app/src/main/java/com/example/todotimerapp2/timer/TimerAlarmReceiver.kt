package com.example.todotimerapp2.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todotimerapp2.R

class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
        val title = intent.getStringExtra("title") ?: "Таймер"

        val channelId = "todo_timer_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Таймеры задач",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)   // <<< ВАЖНО: не 0
            .setContentTitle("Время вышло")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
