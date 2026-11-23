package ru.mydietolog.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.mydietolog.MainActivity
import ru.mydietolog.data.DiaryReminder
import ru.mydietolog.data.MealType
import ru.mydietolog.data.Repository
import ru.mydietolog.data.ReminderSettingsRepository
import java.io.File
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
                ReminderScheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val settings = ReminderSettingsRepository.readStored(context)
        when (intent.getStringExtra(ReminderScheduler.EXTRA_KIND)) {
            ReminderScheduler.KIND_DIARY -> {
                val reminder = settings.diaryReminders.getOrNull(intent.getIntExtra(ReminderScheduler.EXTRA_INDEX, -1))
                if (settings.diaryEnabled && reminder != null && diaryNeeded(context, reminder, LocalDate.now())) {
                    ReminderNotifier.diary(context, reminder.meal)
                }
            }
            ReminderScheduler.KIND_WATER -> if (settings.waterEnabled) ReminderNotifier.water(context)
        }
    }

    /** Общее напоминание молчит, если основные приёмы уже записаны; именное — если записан указанный приём. */
    private fun diaryNeeded(context: Context, reminder: DiaryReminder, today: LocalDate): Boolean {
        val recorded = Repository.readStored(context)
            .filter { it.date == today }
            .map { it.type }
            .toSet()
        return when (val meal = reminder.meal) {
            null -> !MealType.main.all { it in recorded }
            else -> meal !in recorded
        }
    }
}

object ReminderNotifier {

    private const val DIARY_CHANNEL = "diary_reminders"
    private const val WATER_CHANNEL = "water_reminders"
    private const val DIARY_NOTIFICATION_ID = 1
    private const val WATER_NOTIFICATION_ID = 2
    private const val DIARY_REQUEST_CODE = 10
    private const val WATER_REQUEST_CODE = 11

    const val EXTRA_OPEN_DATE = "open_date"

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun diary(context: Context, meal: MealType?) {
        val text = meal?.let { "Запишите ${it.title.lowercase()}" } ?: "Заполните дневник за сегодня"
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_DATE, LocalDate.now().toString())
        show(context, DIARY_CHANNEL, DIARY_NOTIFICATION_ID, "Дневник питания", text, open, DIARY_REQUEST_CODE)
    }

    fun water(context: Context) {
        val open = Intent(context, MainActivity::class.java)
        show(context, WATER_CHANNEL, WATER_NOTIFICATION_ID, "Вода", "Пора выпить воды", open, WATER_REQUEST_CODE)
    }

    @SuppressLint("MissingPermission")
    private fun show(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        open: Intent,
        requestCode: Int,
    ) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(DIARY_CHANNEL, "Дневник питания", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминания заполнить дневник" },
        )
        manager.createNotificationChannel(
            NotificationChannel(WATER_CHANNEL, "Вода", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминания пить воду" },
        )
    }
}
