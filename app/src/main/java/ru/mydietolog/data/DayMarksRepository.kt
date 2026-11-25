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

/** Отметки дня хранятся в day_marks.json во внутренней памяти приложения. */
class DayMarksRepository(context: Context) {

    private val file = File(context.filesDir, "day_marks.json")

    private val _marks = MutableStateFlow<List<DayMarks>>(emptyList())
    val marks: StateFlow<List<DayMarks>> = _marks.asStateFlow()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        _marks.value = runCatching {
            if (!file.exists()) emptyList()
            else {
                val arr = JSONObject(file.readText()).optJSONArray("marks") ?: return@runCatching emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { DayMarks.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            }
        }.getOrElse { emptyList() }
    }

    fun marksOn(date: LocalDate): DayMarks? = _marks.value.firstOrNull { it.date == date }

    suspend fun update(date: LocalDate, change: (DayMarks) -> DayMarks) {
        val current = _marks.value
        val existing = current.firstOrNull { it.date == date } ?: DayMarks(date)
        val changed = change(existing)
        val updated = when {
            changed.isEmpty -> current.filterNot { it.date == date }
            current.any { it.date == date } -> current.map { if (it.date == date) changed else it }
            else -> current + changed
        }
        _marks.value = updated
        persist(updated)
    }

    /** Месячные на каждый день от [from] до [to] включительно. Один проход и одна запись. */
    suspend fun setMenstruation(from: LocalDate, to: LocalDate, value: Boolean?) {
        val start = minOf(from, to)
        val end = maxOf(from, to)
        val current = _marks.value.toMutableList()
        var date = start
        while (!date.isAfter(end)) {
            val index = current.indexOfFirst { it.date == date }
            val existing = if (index >= 0) current[index] else DayMarks(date)
            val changed = existing.copy(menstruation = value)
            when {
                changed.isEmpty -> if (index >= 0) current.removeAt(index)
                index >= 0 -> current[index] = changed
                else -> current.add(changed)
            }
            date = date.plusDays(1)
        }
        val updated = current.toList()
        _marks.value = updated
        persist(updated)
    }

    private suspend fun persist(list: List<DayMarks>) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 1)
            put("marks", JSONArray(list.sortedBy { it.date }.map { it.toJson() }))
        }
        file.writeAtomic(root.toString())
    }
}
