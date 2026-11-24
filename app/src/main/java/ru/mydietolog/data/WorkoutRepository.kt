package ru.mydietolog.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Программа — из файла пользователя (program.json).
 * Факты тренировок — workout_logs.json.
 * Кэш имён своих упражнений — exercise_names.json.
 * Переносы занятий внутри недели — workout_moves.json. Программу не меняют.
 */
class WorkoutRepository(private val context: Context) {

    private val programFile = File(context.filesDir, "program.json")
    private val logsFile = File(context.filesDir, "workout_logs.json")
    private val namesFile = File(context.filesDir, "exercise_names.json")
    private val movesFile = File(context.filesDir, "workout_moves.json")

    private val _program = MutableStateFlow<WorkoutProgram?>(null)
    val program: StateFlow<WorkoutProgram?> = _program.asStateFlow()

    private val _logs = MutableStateFlow<List<WorkoutLog>>(emptyList())
    val logs: StateFlow<List<WorkoutLog>> = _logs.asStateFlow()

    private val _customNames = MutableStateFlow<List<String>>(emptyList())
    val customNames: StateFlow<List<String>> = _customNames.asStateFlow()

    private val _moves = MutableStateFlow<List<SessionMove>>(emptyList())
    val moves: StateFlow<List<SessionMove>> = _moves.asStateFlow()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        _program.value = runCatching {
            if (!programFile.exists()) null
            else WorkoutProgram.fromJson(JSONObject(programFile.readText()))
        }.getOrNull()

        _logs.value = runCatching {
            if (!logsFile.exists()) emptyList()
            else {
                val root = JSONObject(logsFile.readText())
                root.optJSONArray("logs")?.mapObjects { WorkoutLog.fromJson(it) }.orEmpty()
            }
        }.getOrElse { emptyList() }

        _customNames.value = runCatching {
            if (!namesFile.exists()) emptyList()
            else {
                val arr = JSONArray(namesFile.readText())
                (0 until arr.length()).map { arr.getString(it) }
            }
        }.getOrElse { emptyList() }

        _moves.value = runCatching {
            if (!movesFile.exists()) emptyList()
            else {
                val arr = JSONObject(movesFile.readText()).optJSONArray("moves") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { SessionMove.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            }
        }.getOrElse { emptyList() }
    }

    fun logsOn(date: LocalDate): List<WorkoutLog> =
        _logs.value.filter { it.date == date }.sortedBy { it.createdAt }

    /** Последнее выполнение того же упражнения (по id программы или имени). */
    fun lastTimeFor(exerciseId: String?, name: String, before: LocalDate): ExerciseLog? {
        val norm = normalizeExerciseName(name)
        return _logs.value
            .filter { it.date.isBefore(before) }
            .sortedByDescending { it.date }
            .flatMap { it.exercises }
            .firstOrNull { ex ->
                (exerciseId != null && ex.exerciseId == exerciseId) ||
                    normalizeExerciseName(ex.name) == norm
            }
    }

    suspend fun upsertLog(log: WorkoutLog) {
        val current = _logs.value
        val updated = if (current.any { it.id == log.id }) {
            current.map { if (it.id == log.id) log else it }
        } else current + log
        _logs.value = updated
        persistLogs(updated)
    }

    suspend fun deleteLog(id: String) {
        val updated = _logs.value.filterNot { it.id == id }
        _logs.value = updated
        persistLogs(updated)
    }

    suspend fun clearLogs() {
        _logs.value = emptyList()
        persistLogs(emptyList())
    }

    /**
     * Читает JSON программы из Uri. Не сохраняет — только парсит для превью.
     * @throws Exception при ошибке чтения/парсинга
     */
    suspend fun parseProgram(uri: Uri, displayName: String?): WorkoutProgram = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Не удалось прочитать файл")
        } catch (_: SecurityException) {
            error("Нет доступа к файлу. Выберите его ещё раз.")
        }
        val json = JSONObject(text)
        WorkoutProgram.fromJson(json, fileName = displayName).copy(loadedAt = System.currentTimeMillis())
    }

    /** Записывает программу, которую поправили в приложении. Переносы чужих дней недели убирает. */
    suspend fun saveProgram(program: WorkoutProgram) {
        val stored = program.copy(loadedAt = System.currentTimeMillis())
        _program.value = stored
        val validMoves = _moves.value.filter { move ->
            val session = stored.sessions.firstOrNull { it.id == move.sessionId } ?: return@filter false
            move.origin.dayOfWeek.value == session.weekday
        }
        _moves.value = validMoves
        withContext(Dispatchers.IO) {
            programFile.writeAtomic(stored.toJson().toString())
            persistMoves(validMoves)
        }
    }

    /** Удаляет программу с телефона. Факты тренировок остаются. Переносы сбрасываются. */
    suspend fun deleteProgram() {
        _program.value = null
        _moves.value = emptyList()
        withContext(Dispatchers.IO) {
            if (programFile.exists()) programFile.delete()
            persistMoves(emptyList())
        }
    }

    /** Заменяет программу. Логи и «в прошлый раз» не трогаем. Переносы сбрасываем: дни в новом файле свои. */
    suspend fun replaceProgram(program: WorkoutProgram) {
        val stored = program.copy(loadedAt = System.currentTimeMillis())
        _program.value = stored
        _moves.value = emptyList()
        withContext(Dispatchers.IO) {
            programFile.writeAtomic(stored.toJson().toString())
            persistMoves(emptyList())
        }
    }

    suspend fun saveMoves(moves: List<SessionMove>) {
        _moves.value = moves
        persistMoves(moves)
    }

    /** Меняет даты фактов одним сохранением: id лога → новая дата. */
    suspend fun redateLogs(changes: Map<String, LocalDate>) {
        if (changes.isEmpty()) return
        val updated = _logs.value.map { log -> changes[log.id]?.let { log.copy(date = it) } ?: log }
        _logs.value = updated
        persistLogs(updated)
    }

    suspend fun rememberCustomName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val norm = normalizeExerciseName(trimmed)
        val updated = listOf(trimmed) + _customNames.value.filterNot { normalizeExerciseName(it) == norm }
        _customNames.value = updated.take(100)
        persistNames(_customNames.value)
    }

    fun suggestNames(query: String): List<String> {
        val q = normalizeExerciseName(query)
        if (q.isBlank()) return _customNames.value.take(8)
        return _customNames.value.filter { normalizeExerciseName(it).contains(q) }.take(8)
    }

    private suspend fun persistLogs(list: List<WorkoutLog>) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 1)
            put("logs", JSONArray(list.map { it.toJson() }))
        }
        logsFile.writeAtomic(root.toString())
    }

    private suspend fun persistNames(list: List<String>) = withContext(Dispatchers.IO) {
        namesFile.writeAtomic(JSONArray(list).toString())
    }

    private suspend fun persistMoves(list: List<SessionMove>) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 1)
            put("moves", JSONArray(list.map { it.toJson() }))
        }
        movesFile.writeAtomic(root.toString())
    }
}
