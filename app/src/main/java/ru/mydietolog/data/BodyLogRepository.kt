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
import java.time.LocalDate

/** Вес, замеры и Семавик в body_log.json во внутренней памяти приложения. */
class BodyLogRepository(context: Context) {

    private val file = File(context.filesDir, "body_log.json")

    private val _entries = MutableStateFlow<List<BodyLog>>(emptyList())
    val entries: StateFlow<List<BodyLog>> = _entries.asStateFlow()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        _entries.value = runCatching {
            if (!file.exists()) emptyList()
            else {
                val arr = JSONObject(file.readText()).optJSONArray("entries") ?: return@runCatching emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { BodyLog.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }.filterNot { it.isEmpty }
            }
        }.getOrElse { emptyList() }
    }

    suspend fun setWeight(date: LocalDate, weightKg: Double?) {
        update(date) { it.copy(weightKg = weightKg.accepted()) }
    }

    suspend fun setMeasurements(
        date: LocalDate,
        waistCm: Double?,
        hipsCm: Double?,
        chestCm: Double?,
        thighCm: Double?,
        armCm: Double?,
    ) {
        update(date) {
            it.copy(
                waistCm = waistCm.accepted(),
                hipsCm = hipsCm.accepted(),
                chestCm = chestCm.accepted(),
                thighCm = thighCm.accepted(),
                armCm = armCm.accepted(),
            )
        }
    }

    suspend fun setSemavik(date: LocalDate, semavikMg: Double?) {
        update(date) { it.copy(semavikMg = semavikMg.accepted()) }
    }

    private suspend fun update(date: LocalDate, change: (BodyLog) -> BodyLog) {
        val current = _entries.value
        val existing = current.firstOrNull { it.date == date } ?: BodyLog(date)
        val changed = change(existing)
        val updated = when {
            changed.isEmpty -> current.filterNot { it.date == date }
            current.any { it.date == date } -> current.map { if (it.date == date) changed else it }
            else -> current + changed
        }
        _entries.value = updated
        persist(updated)
    }

    private suspend fun persist(list: List<BodyLog>) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 1)
            put("entries", JSONArray(list.sortedBy { it.date }.map { it.toJson() }))
        }
        file.writeAtomic(root.toString())
    }
}

/** Отрицательные и нечисла в файл не попадают. */
private fun Double?.accepted(): Double? {
    val value = this ?: return null
    if (value.isNaN() || value.isInfinite() || value < 0.0) return null
    return value
}
