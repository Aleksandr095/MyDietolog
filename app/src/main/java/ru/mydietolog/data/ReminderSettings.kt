package ru.mydietolog.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val MAX_DIARY_REMINDERS = 5
val WATER_INTERVAL_OPTIONS_HOURS = listOf(1, 2, 3, 4)

private const val MINUTES_IN_DAY = 24 * 60
private const val DEFAULT_DIARY_MINUTE = 21 * 60

data class DiaryReminder(
    /** Минуты от начала суток: 21:00 — 1260. */
    val minuteOfDay: Int,
    /** null — напоминание о дне целиком (пропускается, если записаны завтрак, обед и ужин). */
    val meal: MealType? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("minute", minuteOfDay)
        put("meal", meal?.code ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): DiaryReminder = DiaryReminder(
            minuteOfDay = o.optInt("minute", DEFAULT_DIARY_MINUTE).coerceIn(0, MINUTES_IN_DAY - 1),
            meal = if (o.isNull("meal")) null else MealType.entries.firstOrNull { it.code == o.optString("meal") },
        )
    }
}

data class ReminderSettings(
    val diaryEnabled: Boolean = true,
    val diaryReminders: List<DiaryReminder> = listOf(DiaryReminder(DEFAULT_DIARY_MINUTE)),
    val waterEnabled: Boolean = false,
    val waterIntervalHours: Int = 2,
    val waterFromMinute: Int = 9 * 60,
    val waterToMinute: Int = 21 * 60,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("diary_enabled", diaryEnabled)
        put("diary_reminders", JSONArray(diaryReminders.map { it.toJson() }))
        put("water_enabled", waterEnabled)
        put("water_interval_hours", waterIntervalHours)
        put("water_from_minute", waterFromMinute)
        put("water_to_minute", waterToMinute)
    }

    companion object {
        fun fromJson(o: JSONObject): ReminderSettings {
            val defaults = ReminderSettings()
            val reminders = o.optJSONArray("diary_reminders")
                ?.let { arr -> (0 until arr.length()).map { DiaryReminder.fromJson(arr.getJSONObject(it)) } }
                ?.take(MAX_DIARY_REMINDERS)
                ?: defaults.diaryReminders
            return ReminderSettings(
                diaryEnabled = o.optBoolean("diary_enabled", defaults.diaryEnabled),
                diaryReminders = reminders,
                waterEnabled = o.optBoolean("water_enabled", defaults.waterEnabled),
                waterIntervalHours = o.optInt("water_interval_hours", defaults.waterIntervalHours)
                    .takeIf { it in WATER_INTERVAL_OPTIONS_HOURS } ?: defaults.waterIntervalHours,
                waterFromMinute = o.optInt("water_from_minute", defaults.waterFromMinute).coerceIn(0, MINUTES_IN_DAY - 1),
                waterToMinute = o.optInt("water_to_minute", defaults.waterToMinute).coerceIn(0, MINUTES_IN_DAY - 1),
            )
        }
    }
}

/** Настройки напоминаний хранятся в reminder_settings.json. */
class ReminderSettingsRepository(private val context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    private val _settings = MutableStateFlow(ReminderSettings())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        _settings.value = readFile(file)
    }

    suspend fun update(change: (ReminderSettings) -> ReminderSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        withContext(Dispatchers.IO) { file.writeAtomic(updated.toJson().toString()) }
    }

    companion object {
        private const val FILE_NAME = "reminder_settings.json"

        /** Синхронное чтение для приёмников и планировщика при старте. */
        fun readStored(context: Context): ReminderSettings = readFile(File(context.filesDir, FILE_NAME))

        private fun readFile(file: File): ReminderSettings = runCatching {
            if (!file.exists()) ReminderSettings() else ReminderSettings.fromJson(JSONObject(file.readText()))
        }.getOrElse { ReminderSettings() }
    }
}
