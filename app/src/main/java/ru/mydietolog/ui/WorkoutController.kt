package ru.mydietolog.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ProgramSession
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.WorkoutProgram
import ru.mydietolog.data.WorkoutRepository
import ru.mydietolog.data.formatLastTime
import java.time.LocalDate

enum class WeekDayMark { EMPTY, PLANNED, PARTIAL, DONE }

/** Навигация, которую контроллер тренировок просит у общего ViewModel. */
interface WorkoutNav {
    val current: Screen
    fun push(screen: Screen)
    fun popOne()
    fun popToWorkoutsHome()
    fun today(): LocalDate
}

/**
 * Состояние вкладки «Тренировки»: программа, факт занятия, своё упражнение.
 * Дневник питания сюда не входит.
 */
class WorkoutController(
    private val repo: WorkoutRepository,
    private val scope: CoroutineScope,
    private val nav: WorkoutNav,
) {
    val program = repo.program
    val logs = repo.logs
    val customNames = repo.customNames

    var draft by mutableStateOf<WorkoutDraft?>(null)
        private set

    var customName by mutableStateOf("")
    var customSets by mutableStateOf("3")
    var customWeight by mutableStateOf("")
    var customReps by mutableStateOf("10")

    var programPreview by mutableStateOf<WorkoutProgram?>(null)
        private set
    var programError by mutableStateOf<String?>(null)
        private set

    var selectedDate by mutableStateOf(nav.today())
        private set

    private var openedSnapshot: WorkoutLog? = null
    private var createdFreshLog: Boolean = false

    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    fun openPlan(date: LocalDate) {
        if (program.value?.sessionFor(date) != null) nav.push(Screen.WorkoutPlan(date))
    }

    fun openProgram() {
        programPreview = null
        programError = null
        nav.push(Screen.WorkoutProgram)
    }

    fun pickProgramFile(uri: Uri, displayName: String?) {
        programError = null
        scope.launch {
            try {
                programPreview = repo.parseProgram(uri, displayName)
            } catch (e: Exception) {
                programPreview = null
                programError = e.message ?: "Не удалось прочитать файл"
            }
        }
    }

    fun confirmReplaceProgram() {
        val preview = programPreview ?: return
        scope.launch {
            repo.replaceProgram(preview)
            programPreview = null
            programError = null
        }
    }

    fun startOrOpenSession(date: LocalDate, session: ProgramSession) {
        scope.launch {
            val existing = repo.logsOn(date).firstOrNull { it.sessionId == session.id }
            val log = existing ?: WorkoutLog.fromProgramSession(date, session)
            if (existing == null) repo.upsertLog(log)
            show(log, createdFresh = existing == null)
        }
    }

    fun startOutsidePlan(date: LocalDate = selectedDate) {
        scope.launch {
            val log = WorkoutLog.outsidePlan(date)
            repo.upsertLog(log)
            show(log, createdFresh = true)
        }
    }

    fun openExisting(log: WorkoutLog) = show(log, createdFresh = false)

    private fun show(log: WorkoutLog, createdFresh: Boolean) {
        openedSnapshot = log
        createdFreshLog = createdFresh
        draft = WorkoutDraft.from(log, lastTimeResolver(repo::lastTimeFor, log.date))
        if (nav.current !is Screen.WorkoutSession) nav.push(Screen.WorkoutSession)
    }

    /** Сохранить факт и вернуться на главный экран тренировок. */
    fun finish() {
        val current = draft ?: run { close(); return }
        scope.launch {
            repo.upsertLog(current.toLog())
            close()
        }
    }

    /** Отменить правки: вернуть состояние на момент открытия или удалить пустую новую запись. */
    fun cancel() {
        val snap = openedSnapshot
        val current = draft
        scope.launch {
            when {
                snap == null -> Unit
                createdFreshLog && current?.toLog()?.hasProgress != true -> repo.deleteLog(snap.id)
                else -> repo.upsertLog(snap)
            }
            close()
        }
    }

    private fun close() {
        openedSnapshot = null
        createdFreshLog = false
        draft = null
        nav.popToWorkoutsHome()
    }

    fun openCustomExercise() {
        customName = ""
        customSets = "3"
        customWeight = ""
        customReps = "10"
        nav.push(Screen.CustomExercise)
    }

    fun nameSuggestions(): List<String> = repo.suggestNames(customName)

    fun addCustomExercise() {
        val current = draft ?: return
        val name = customName.trim()
        if (name.isBlank()) return
        val setCount = customSets.toIntOrNull()?.coerceIn(1, 12) ?: 3
        val reps = customReps
        current.exercises.add(
            ExerciseDraft(
                name = name,
                target = "$setCount × ${reps.ifBlank { "—" }}",
                kind = ExerciseKind.STRENGTH,
                isCustom = true,
                sets = List(setCount) { SetDraft(weight = customWeight.replace(',', '.'), reps = reps) },
                lastTimeLabel = formatLastTime(repo.lastTimeFor(null, name, current.date)),
            ),
        )
        scope.launch {
            repo.rememberCustomName(name)
            repo.upsertLog(current.toLog())
        }
        if (nav.current is Screen.CustomExercise) nav.popOne()
    }

    fun addSet(ex: ExerciseDraft) {
        val last = ex.sets.lastOrNull()
        ex.sets.add(SetDraft(weight = last?.weight.orEmpty(), reps = last?.reps.orEmpty()))
    }

    fun toggleDone(ex: ExerciseDraft) {
        ex.done = !ex.done
        if (ex.done) markFilledSets(ex)
        persistQuiet()
    }

    fun markPlannedDone() {
        draft?.exercises?.filterNot { it.isCustom }?.forEach { ex ->
            ex.done = true
            markFilledSets(ex)
        }
        persistQuiet()
    }

    fun persistQuiet() {
        val current = draft ?: return
        scope.launch { repo.upsertLog(current.toLog()) }
    }

    fun weekMark(date: LocalDate): WeekDayMark {
        val dayLogs = repo.logsOn(date)
        val planned = program.value?.sessionFor(date) != null
        return when {
            dayLogs.any { it.isFullyDone } -> WeekDayMark.DONE
            dayLogs.any { it.hasProgress || it.isPartial } -> WeekDayMark.PARTIAL
            planned -> WeekDayMark.PLANNED
            else -> WeekDayMark.EMPTY
        }
    }

    private fun markFilledSets(ex: ExerciseDraft) {
        if (ex.kind != ExerciseKind.STRENGTH) return
        ex.sets.forEach { set ->
            if (set.reps.isNotBlank() || set.weight.isNotBlank()) set.done = true
        }
    }
}
