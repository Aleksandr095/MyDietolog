package ru.mydietolog.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.mydietolog.data.DiaryReminder
import ru.mydietolog.data.MAX_DIARY_REMINDERS
import ru.mydietolog.data.ReminderSettings
import ru.mydietolog.data.ReminderSettingsRepository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ставит напоминания через AlarmManager (без точных алармов: задержка в пределах минут допустима).
 * Вызывается при старте приложения, после изменения настроек, после перезагрузки и после срабатывания.
 */
object ReminderScheduler {

    const val EXTRA_KIND = "reminder_kind"
    const val EXTRA_INDEX = "reminder_index"
    const val KIND_DIARY = "diary"
    const val KIND_WATER = "water"

    private const val DIARY_CODE_BASE = 100
    private const val WATER_CODE = 200

    fun rescheduleAll(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val settings = ReminderSettingsRepository.readStored(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        cancelAll(context, alarms)

        if (settings.diaryEnabled) {
            settings.diaryReminders.take(MAX_DIARY_REMINDERS).forEachIndexed { index, reminder ->
                schedule(
                    context,
                    alarms,
                    code = DIARY_CODE_BASE + index,
                    at = nextDiaryTime(now, reminder),
                    intent = receiverIntent(context, KIND_DIARY, index),
                )
            }
        }
        if (settings.waterEnabled) {
            nextWaterTime(now, settings)?.let { at ->
                schedule(context, alarms, WATER_CODE, at, receiverIntent(context, KIND_WATER, 0))
            }
        }
    }

    private fun cancelAll(context: Context, alarms: AlarmManager) {
        (DIARY_CODE_BASE until DIARY_CODE_BASE + MAX_DIARY_REMINDERS).forEach { cancel(context, alarms, it) }
        cancel(context, alarms, WATER_CODE)
    }

    private fun cancel(context: Context, alarms: AlarmManager, code: Int) {
        val existing = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    private fun schedule(context: Context, alarms: AlarmManager, code: Int, at: LocalDateTime, intent: Intent) {
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    private fun receiverIntent(context: Context, kind: String, index: Int): Intent =
        Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_INDEX, index)
}

internal fun nextDiaryTime(now: LocalDateTime, reminder: DiaryReminder): LocalDateTime {
    val today = now.toLocalDate().atStartOfDay().plusMinutes(reminder.minuteOfDay.toLong())
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/** Ближайший слот воды в окне [from, to] с шагом интервала; null, если окно пустое. */
internal fun nextWaterTime(now: LocalDateTime, settings: ReminderSettings): LocalDateTime? {
    val step = settings.waterIntervalHours * 60
    val from = settings.waterFromMinute
    val to = settings.waterToMinute
    if (step <= 0 || to < from) return null

    val startOfToday = now.toLocalDate().atStartOfDay()
    for (dayOffset in 0L..1L) {
        var minute = from
        while (minute <= to) {
            val candidate = startOfToday.plusDays(dayOffset).plusMinutes(minute.toLong())
            if (candidate.isAfter(now)) return candidate
            minute += step
        }
    }
    return null
}
