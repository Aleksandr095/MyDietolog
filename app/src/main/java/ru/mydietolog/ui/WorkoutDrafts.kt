package ru.mydietolog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ExerciseLog
import ru.mydietolog.data.SetLog
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.formatLastTime
import ru.mydietolog.data.formatWeight
import ru.mydietolog.data.planNumber
import ru.mydietolog.data.planWhole
import ru.mydietolog.data.plannedSetCount
import java.time.LocalDate
import java.util.UUID

class SetDraft(
    weight: String = "",
    reps: String = "",
    done: Boolean = false,
) {
    var weight by mutableStateOf(weight)
    var reps by mutableStateOf(reps)
    var done by mutableStateOf(done)

    fun toSetLog() = SetLog(
        weightKg = weight.replace(',', '.').toFloatOrNull(),
        reps = reps.toIntOrNull(),
        done = done,
    )

    companion object {
        fun from(s: SetLog) = SetDraft(
            weight = s.weightKg?.let { if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString() } ?: "",
            reps = s.reps?.toString() ?: "",
            done = s.done,
        )
    }
}

class ExerciseDraft(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String? = null,
    name: String,
    val goalSets: Int? = null,
    val goalReps: String = "",
    val goalWeight: String = "",
    val goalMinutes: Int? = null,
    val note: String = "",
    val techniqueUrl: String? = null,
    val kind: ExerciseKind = ExerciseKind.STRENGTH,
    val isCustom: Boolean = false,
    done: Boolean = false,
    sets: List<SetDraft> = emptyList(),
    minutes: String = "",
    val lastTimeLabel: String = "первый раз",
) {
    var name by mutableStateOf(name)
    var done by mutableStateOf(done)
    val sets: SnapshotStateList<SetDraft> = mutableStateListOf<SetDraft>().apply { addAll(sets) }
    var minutes by mutableStateOf(minutes)

    fun toExerciseLog() = ExerciseLog(
        id = id,
        exerciseId = exerciseId,
        name = name.trim(),
        goalSets = goalSets,
        goalReps = goalReps,
        goalWeight = goalWeight,
        goalMinutes = goalMinutes,
        note = note,
        techniqueUrl = techniqueUrl,
        kind = kind,
        isCustom = isCustom,
        done = done,
        sets = if (kind == ExerciseKind.CARDIO) emptyList() else sets.map { it.toSetLog() },
        minutes = if (kind == ExerciseKind.CARDIO) minutes.toIntOrNull() else null,
    )

    /** Пустые поля факта заполняются числами из плана. Уже введённое не затирается. */
    fun fillBlankFromPlan(): Boolean {
        var changed = false
        if (kind == ExerciseKind.CARDIO) {
            if (minutes.isBlank() && goalMinutes != null) {
                minutes = goalMinutes.toString()
                changed = true
            }
            return changed
        }
        val weight = planNumber(goalWeight)?.let { formatWeight(it) }.orEmpty()
        val reps = planWhole(goalReps)?.toString().orEmpty()
        val untouched = sets.all { it.weight.isBlank() && it.reps.isBlank() && !it.done }
        if (untouched && (weight.isNotBlank() || reps.isNotBlank())) {
            sets.clear()
            repeat(plannedSetCount(goalSets)) { sets.add(SetDraft(weight = weight, reps = reps)) }
            return true
        }
        sets.forEach { set ->
            if (set.weight.isBlank() && weight.isNotBlank()) {
                set.weight = weight
                changed = true
            }
            if (set.reps.isBlank() && reps.isNotBlank()) {
                set.reps = reps
                changed = true
            }
        }
        return changed
    }

    companion object {
        fun from(ex: ExerciseLog, lastTimeLabel: String) = ExerciseDraft(
            id = ex.id,
            exerciseId = ex.exerciseId,
            name = ex.name,
            goalSets = ex.goalSets,
            goalReps = ex.goalReps,
            goalWeight = ex.goalWeight,
            goalMinutes = ex.goalMinutes,
            note = ex.note,
            techniqueUrl = ex.techniqueUrl,
            kind = ex.kind,
            isCustom = ex.isCustom,
            done = ex.done,
            sets = ex.sets.map { SetDraft.from(it) }.ifEmpty {
                if (ex.kind == ExerciseKind.STRENGTH) listOf(SetDraft()) else emptyList()
            },
            minutes = ex.minutes?.toString() ?: "",
            lastTimeLabel = lastTimeLabel,
        ).also { it.fillBlankFromPlan() }
    }
}

class WorkoutDraft(
    val id: String,
    val date: LocalDate,
    val sessionId: String?,
    sessionName: String,
    exercises: List<ExerciseDraft>,
    val createdAt: Long,
    val cancelled: Boolean = false,
) {
    var sessionName by mutableStateOf(sessionName)
    val exercises: SnapshotStateList<ExerciseDraft> =
        mutableStateListOf<ExerciseDraft>().apply { addAll(exercises) }

    fun toLog() = WorkoutLog(
        id = id,
        date = date,
        sessionId = sessionId,
        sessionName = sessionName,
        exercises = exercises.map { it.toExerciseLog() },
        createdAt = createdAt,
        cancelled = cancelled,
    )

    companion object {
        fun from(log: WorkoutLog, lastTime: (ExerciseLog) -> String) = WorkoutDraft(
            id = log.id,
            date = log.date,
            sessionId = log.sessionId,
            sessionName = log.sessionName,
            exercises = log.exercises.map { ExerciseDraft.from(it, lastTime(it)) },
            createdAt = log.createdAt,
            cancelled = log.cancelled,
        )
    }
}

fun lastTimeResolver(
    find: (exerciseId: String?, name: String, before: LocalDate) -> ExerciseLog?,
    date: LocalDate,
): (ExerciseLog) -> String = { ex ->
    formatLastTime(find(ex.exerciseId, ex.name, date))
}
