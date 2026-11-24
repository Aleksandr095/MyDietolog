package ru.mydietolog.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class ExerciseKind(val code: String) {
    STRENGTH("strength"),
    CARDIO("cardio");

    companion object {
        fun fromCode(code: String): ExerciseKind =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: STRENGTH
    }
}

/** Упражнение в файле программы. */
data class ProgramExercise(
    val id: String,
    val name: String,
    val goalSets: Int? = null,
    val goalReps: String = "",
    val goalWeight: String = "",
    val goalMinutes: Int? = null,
    val note: String = "",
    val techniqueUrl: String? = null,
    val kind: ExerciseKind = ExerciseKind.STRENGTH,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("sets", goalSets ?: JSONObject.NULL)
        put("reps", goalReps)
        put("weight_kg", goalWeight)
        put("minutes", goalMinutes ?: JSONObject.NULL)
        put("note", note)
        put("technique_url", techniqueUrl ?: JSONObject.NULL)
        put("kind", kind.code)
    }

    companion object {
        fun fromJson(o: JSONObject): ProgramExercise {
            val legacy = parseLegacyTarget(o.optString("target", ""))
            return ProgramExercise(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name", ""),
                goalSets = o.optIntOrNull("sets") ?: legacy.sets,
                goalReps = o.optString("reps", "").ifBlank { legacy.reps },
                goalWeight = o.optString("weight_kg", "").ifBlank { legacy.weight },
                goalMinutes = o.optIntOrNull("minutes") ?: legacy.minutes,
                note = o.optString("note", ""),
                techniqueUrl = o.optStringOrNull("technique_url"),
                kind = ExerciseKind.fromCode(o.optString("kind", "strength")),
            )
        }
    }
}

/** Тип занятия в программе (привязан к дню недели 1=Пн … 7=Вс). */
data class ProgramSession(
    val id: String,
    val name: String,
    val weekday: Int,
    val exercises: List<ProgramExercise> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("weekday", weekday)
        put("exercises", JSONArray(exercises.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): ProgramSession = ProgramSession(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", ""),
            weekday = o.optInt("weekday", 1).coerceIn(1, 7),
            exercises = o.optJSONArray("exercises")?.mapObjects { ProgramExercise.fromJson(it) }.orEmpty(),
        )
    }
}

data class WorkoutProgram(
    val id: String,
    val title: String,
    val fileName: String? = null,
    val loadedAt: Long = System.currentTimeMillis(),
    val sessions: List<ProgramSession> = emptyList(),
) {
    fun sessionFor(date: LocalDate): ProgramSession? =
        sessions.firstOrNull { it.weekday == date.dayOfWeek.value }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("file_name", fileName ?: JSONObject.NULL)
        put("loaded_at", loadedAt)
        put("sessions", JSONArray(sessions.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject, fileName: String? = null): WorkoutProgram = WorkoutProgram(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title", "Программа"),
            fileName = fileName ?: o.optStringOrNull("file_name"),
            loadedAt = o.optLong("loaded_at", System.currentTimeMillis()),
            sessions = o.optJSONArray("sessions")?.mapObjects { ProgramSession.fromJson(it) }.orEmpty(),
        )
    }
}

/** Одно занятие сдвинуто с дня по программе на другую дату. Файл программы не меняется. */
data class SessionMove(
    val sessionId: String,
    val origin: LocalDate,
    val date: LocalDate,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("session_id", sessionId)
        put("origin", origin.toString())
        put("date", date.toString())
    }

    companion object {
        fun fromJson(o: JSONObject): SessionMove = SessionMove(
            sessionId = o.getString("session_id"),
            origin = LocalDate.parse(o.getString("origin")),
            date = LocalDate.parse(o.getString("date")),
        )
    }
}

fun programOrigin(session: ProgramSession, dateInWeek: LocalDate): LocalDate =
    dateInWeek.weekStart().plusDays((session.weekday - 1).toLong())

/** Занятие на дату с учётом переносов внутри недели. */
fun sessionOn(program: WorkoutProgram, moves: List<SessionMove>, date: LocalDate): ProgramSession? {
    moves.firstOrNull { it.date == date }?.let { move ->
        program.sessions.firstOrNull { it.id == move.sessionId }?.let { return it }
    }
    val natural = program.sessionFor(date) ?: return null
    val origin = programOrigin(natural, date)
    val movedAway = moves.any { it.sessionId == natural.id && it.origin == origin && it.date != origin }
    return if (movedAway) null else natural
}

/** День по программе, если это занятие стоит на другой дате. */
fun movedFrom(session: ProgramSession, date: LocalDate, moves: List<SessionMove>): LocalDate? {
    val origin = programOrigin(session, date)
    val moved = moves.any { it.sessionId == session.id && it.origin == origin && it.date == date && date != origin }
    return if (moved) origin else null
}

data class SetLog(
    val weightKg: Float? = null,
    val reps: Int? = null,
    val done: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("weight_kg", weightKg ?: JSONObject.NULL)
        put("reps", reps ?: JSONObject.NULL)
        put("done", done)
    }

    companion object {
        fun fromJson(o: JSONObject): SetLog = SetLog(
            weightKg = o.optFloatOrNull("weight_kg"),
            reps = o.optIntOrNull("reps"),
            done = o.optBoolean("done", false),
        )
    }
}

data class ExerciseLog(
    val id: String = UUID.randomUUID().toString(),
    /** Id из программы; null у своих. */
    val exerciseId: String? = null,
    val name: String,
    val goalSets: Int? = null,
    val goalReps: String = "",
    val goalWeight: String = "",
    val goalMinutes: Int? = null,
    val note: String = "",
    val techniqueUrl: String? = null,
    val kind: ExerciseKind = ExerciseKind.STRENGTH,
    val isCustom: Boolean = false,
    val done: Boolean = false,
    val sets: List<SetLog> = emptyList(),
    val minutes: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("exercise_id", exerciseId ?: JSONObject.NULL)
        put("name", name)
        put("goal_sets", goalSets ?: JSONObject.NULL)
        put("goal_reps", goalReps)
        put("goal_weight_kg", goalWeight)
        put("goal_minutes", goalMinutes ?: JSONObject.NULL)
        put("note", note)
        put("technique_url", techniqueUrl ?: JSONObject.NULL)
        put("kind", kind.code)
        put("is_custom", isCustom)
        put("done", done)
        put("sets", JSONArray(sets.map { it.toJson() }))
        put("minutes", minutes ?: JSONObject.NULL)
    }

    companion object {
        fun fromProgram(ex: ProgramExercise): ExerciseLog {
            val sets = if (ex.kind == ExerciseKind.CARDIO) emptyList()
            else List(plannedSetCount(ex.goalSets)) {
                SetLog(weightKg = planNumber(ex.goalWeight), reps = planWhole(ex.goalReps))
            }
            return ExerciseLog(
                exerciseId = ex.id,
                name = ex.name,
                goalSets = ex.goalSets,
                goalReps = ex.goalReps,
                goalWeight = ex.goalWeight,
                goalMinutes = ex.goalMinutes,
                note = ex.note,
                techniqueUrl = ex.techniqueUrl,
                kind = ex.kind,
                isCustom = false,
                sets = sets,
                minutes = if (ex.kind == ExerciseKind.CARDIO) ex.goalMinutes else null,
            )
        }

        fun fromJson(o: JSONObject): ExerciseLog {
            val legacy = parseLegacyTarget(o.optString("target", ""))
            return ExerciseLog(
            id = o.optString("id", UUID.randomUUID().toString()),
            exerciseId = o.optStringOrNull("exercise_id"),
            name = o.optString("name", ""),
            goalSets = o.optIntOrNull("goal_sets") ?: legacy.sets,
            goalReps = o.optString("goal_reps", "").ifBlank { legacy.reps },
            goalWeight = o.optString("goal_weight_kg", "").ifBlank { legacy.weight },
            goalMinutes = o.optIntOrNull("goal_minutes") ?: legacy.minutes,
            note = o.optString("note", ""),
            techniqueUrl = o.optStringOrNull("technique_url"),
            kind = ExerciseKind.fromCode(o.optString("kind", "strength")),
            isCustom = o.optBoolean("is_custom", false),
            done = o.optBoolean("done", false),
            sets = o.optJSONArray("sets")?.mapObjects { SetLog.fromJson(it) }.orEmpty(),
            minutes = o.optIntOrNull("minutes"),
        )
        }
    }
}

data class WorkoutLog(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val sessionId: String? = null,
    val sessionName: String,
    val exercises: List<ExerciseLog> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Тренировка не выполняется, запись остаётся. */
    val cancelled: Boolean = false,
) {
    val doneCount: Int get() = exercises.count { it.done }
    val isFullyDone: Boolean get() = exercises.isNotEmpty() && exercises.all { it.done }
    val isPartial: Boolean get() = doneCount > 0 && !isFullyDone
    val hasProgress: Boolean get() = exercises.any { it.done || it.sets.any { s -> s.done } || (it.minutes ?: 0) > 0 }

    /** Есть что сохранять: название, вес, повторы или отметка. */
    val hasContent: Boolean get() = exercises.any { ex ->
        ex.name.isNotBlank() || ex.done || (ex.minutes ?: 0) > 0 ||
            ex.sets.any { s -> s.done || s.reps != null || s.weightKg != null }
    }

    fun markLabel(): String = when {
        cancelled -> "отменена"
        isFullyDone -> "завершено"
        isPartial || hasProgress -> "частично"
        else -> "есть запись"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date.toString())
        put("session_id", sessionId ?: JSONObject.NULL)
        put("session_name", sessionName)
        put("created_at", createdAt)
        put("cancelled", cancelled)
        put("exercises", JSONArray(exercises.map { it.toJson() }))
    }

    /** JSON для недельной выгрузки: тот же лог плюс статус для читателя архива. */
    fun toExportJson(): JSONObject = toJson().apply {
        put("outside_plan", sessionId == null)
        put("status", markLabel())
    }

    companion object {
        fun fromProgramSession(date: LocalDate, session: ProgramSession): WorkoutLog =
            WorkoutLog(
                date = date,
                sessionId = session.id,
                sessionName = session.name,
                exercises = session.exercises.map { ExerciseLog.fromProgram(it) },
            )

        fun outsidePlan(date: LocalDate): WorkoutLog =
            WorkoutLog(date = date, sessionId = null, sessionName = "Вне плана")

        fun fromJson(o: JSONObject): WorkoutLog = WorkoutLog(
            id = o.optString("id", UUID.randomUUID().toString()),
            date = LocalDate.parse(o.getString("date")),
            sessionId = o.optStringOrNull("session_id"),
            sessionName = o.optString("session_name", "Тренировка"),
            createdAt = o.optLong("created_at", 0L),
            cancelled = o.optBoolean("cancelled", false),
            exercises = o.optJSONArray("exercises")?.mapObjects { ExerciseLog.fromJson(it) }.orEmpty(),
        )
    }
}

/** Краткое описание прошлого выполнения для подсказки «в прошлый раз». */
fun formatLastTime(log: ExerciseLog?): String {
    if (log == null) return "первый раз"
    return when (log.kind) {
        ExerciseKind.CARDIO -> log.minutes?.let { "$it мин" } ?: "первый раз"
        ExerciseKind.STRENGTH -> {
            val doneSets = log.sets.filter { it.done || it.reps != null }
            if (doneSets.isEmpty()) return "первый раз"
            val weight = doneSets.mapNotNull { it.weightKg }.lastOrNull()
            val reps = doneSets.mapNotNull { it.reps }.joinToString(", ")
            buildList {
                weight?.let { add("${formatWeight(it)} кг") }
                if (reps.isNotBlank()) add(reps)
            }.joinToString(" · ").ifBlank { "первый раз" }
        }
    }
}

fun formatWeight(w: Float): String =
    if (w == w.toLong().toFloat()) w.toLong().toString() else w.toString()

fun normalizeExerciseName(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ")

/** Сколько строк подходов создать в факте, если в плане указано число. */
fun plannedSetCount(sets: Int?): Int = (sets ?: 3).coerceIn(1, 12)

/** Первое число из плана: вес «8» или «8–10» становится 8. */
fun planNumber(text: String): Float? =
    Regex("""\d+(?:[.,]\d+)?""").find(text)?.value?.replace(',', '.')?.toFloatOrNull()

/** Первое целое из плана: повторы «10–12» или «40 сек» становятся 10 и 40. */
fun planWhole(text: String): Int? =
    Regex("""\d+""").find(text)?.value?.toIntOrNull()

data class ParsedGoal(
    val sets: Int?,
    val reps: String,
    val weight: String,
    val minutes: Int?,
)

/** Старые программы хранили цель одной строкой «3 × 10–12» или «40 мин». */
fun parseLegacyTarget(target: String): ParsedGoal {
    val text = target.trim()
    if (text.isEmpty()) return ParsedGoal(null, "", "", null)
    Regex("""^(\d+)\s*мин\.?$""").find(text)?.let { match ->
        return ParsedGoal(null, "", "", match.groupValues[1].toIntOrNull())
    }
    Regex("""^(\d+)\s*[×xX*]\s*(.+)$""").find(text)?.let { match ->
        return ParsedGoal(match.groupValues[1].toIntOrNull(), match.groupValues[2].trim(), "", null)
    }
    return ParsedGoal(null, text, "", null)
}

/** Подходы, повторы и вес отдельными частями, без записи «3 × 10–12». */
fun prescriptionLine(sets: Int?, reps: String, weight: String, minutes: Int? = null): String = buildList {
    sets?.let { add("подходы $it") }
    if (reps.isNotBlank()) {
        add(if (reps.contains("сек")) reps else "повторы $reps")
    }
    if (weight.isNotBlank()) add("вес $weight кг")
    minutes?.let { add("минуты $it") }
}.joinToString(", ")

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }

internal fun JSONObject.optFloatOrNull(key: String): Float? =
    if (!has(key) || isNull(key)) null else optDouble(key).toFloat()

internal inline fun <T> JSONArray.mapObjects(crossinline block: (JSONObject) -> T): List<T> =
    (0 until length()).map { block(getJSONObject(it)) }
