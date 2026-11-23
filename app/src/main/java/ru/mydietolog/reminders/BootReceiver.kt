package ru.mydietolog.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager сбрасывает будильники при перезагрузке и обновлении приложения — ставим заново. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderScheduler.rescheduleAll(context)
        }
    }
}
