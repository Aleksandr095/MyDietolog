package ru.mydietolog.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ProgramSession
import ru.mydietolog.data.SessionMove
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.WorkoutProgram
import ru.mydietolog.data.WorkoutRepository
import ru.mydietolog.data.formatLastTime
import ru.mydietolog.data.programOrigin
import ru.mydietolog.data.weekStart
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
    val moves = repo.moves

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
    var programEdit by mutableStateOf<ProgramEdit?>(null)
        private set

    var selectedDate by mutableStateOf(nav.today())
        private set

    private var openedSnapshot: WorkoutLog? = null
    private var createdFreshLog: Boolean = false

    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    fun openPlan(date: LocalDate) {
        if (sessionOn(date) != null) nav.push(Screen.WorkoutPlan(date))
    }

    fun sessionOn(date: LocalDate): ProgramSession? {
        val current = program.value ?: return null
        return ru.mydietolog.data.sessionOn(current, moves.value, date)
    }

    /**
     * Переносит занятие с [from] на [to] в пределах одной недели.
     * Если на [to] уже есть занятие, они меняются местами. Факт едет вместе с занятием.
     */
    fun moveSession(from: LocalDate, to: LocalDate) {
        if (from == to || from.weekStart() != to.weekStart()) return
        val current = program.value ?: return
        val session = ru.mydietolog.data.sessionOn(current, moves.value, from) ?: return
        val other = ru.mydietolog.data.sessionOn(current, moves.value, to)
        val week = from.weekStart()
        val updated = moves.value.toMutableList()
        fun place(sessionId: String, origin: LocalDate, date: LocalDate) {
            updated.removeAll { it.sessionId == sessionId && it.origin == origin }
            if (date != origin) updated.add(SessionMove(sessionId, origin, date))
        }
        place(session.id, programOrigin(session, week), to)
        if (other != null) place(other.id, programOrigin(other, week), from)
        scope.launch {
            val changes = buildMap {
                logs.value.firstOrNull { it.date == from && it.sessionId == session.id }?.let { put(it.id, to) }
                if (other != null) {
                    logs.value.firstOrNull { it.date == to && it.sessionId == other.id }?.let { put(it.id, from) }
                }
            }
            repo.saveMoves(updated)
            repo.redateLogs(changes)
            selectDate(to)
            if (nav.current is Screen.WorkoutPlan) nav.popOne()
        }
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
        val next = WorkoutDraft.from(log, lastTimeResolver(repo::lastTimeFor, log.date))
        val filled = next.toLog()
        openedSnapshot = filled
        createdFreshLog = createdFresh
        draft = next
        if (filled.exercises != log.exercises) {
            scope.launch { repo.upsertLog(filled) }
        }
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

    /** Отменить правки экрана. Пустую новую запись удаляем. Внеплановую с упражнениями оставляем. */
    fun cancel() {
        val snap = openedSnapshot
        val current = draft
        scope.launch {
            when {
                snap == null -> Unit
                createdFreshLog && snap.sessionId == null && current?.toLog()?.hasContent == true ->
                    repo.upsertLog(current.toLog())
                createdFreshLog && snap.sessionId == null -> repo.deleteLog(snap.id)
                createdFreshLog && current?.toLog()?.hasProgress != true -> repo.deleteLog(snap.id)
                else -> repo.upsertLog(snap)
            }
            close()
        }
    }

    fun openProgramEditor() {
        programEdit = program.value?.let { ProgramEdit.from(it) } ?: ProgramEdit.blank()
        if (nav.current !is Screen.ProgramEdit) nav.push(Screen.ProgramEdit)
    }

    fun saveProgramEditor() {
        val edit = programEdit ?: return
        scope.launch {
            repo.saveProgram(edit.toProgram())
            programEdit = null
            if (nav.current is Screen.ProgramEdit) nav.popOne()
        }
    }

    fun clearHistory() {
        scope.launch { repo.clearLogs() }
    }

    fun deleteProgram() {
        scope.launch {
            repo.deleteProgram()
            programEdit = null
            programPreview = null
            programError = null
            if (nav.current is Screen.ProgramEdit) nav.popOne()
        }
    }

    fun setSessionWeekday(session: SessionEdit, weekday: Int) {
        val edit = programEdit ?: return
        val day = weekday.coerceIn(1, 7)
        edit.sessions.firstOrNull { it !== session && it.weekday == day }?.let { other ->
            other.weekday = session.weekday
        }
        session.weekday = day
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
                goalSets = setCount,
                goalReps = reps,
                goalWeight = customWeight.trim(),
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
        setExerciseDone(ex, !ex.done)
        persistQuiet()
    }

    /** Все упражнения и подходы отмечены, статус станет «завершено». На диск не пишется. */
    fun completeDraft() {
        draft?.exercises?.forEach { setExerciseDone(it, true) }
    }

    fun persistQuiet() {
        val current = draft ?: return
        scope.launch { repo.upsertLog(current.toLog()) }
    }

    fun weekMark(date: LocalDate): WeekDayMark {
        val dayLogs = repo.logsOn(date)
        val planned = sessionOn(date) != null
        val active = dayLogs.filter { !it.cancelled }
        return when {
            active.any { it.isFullyDone } -> WeekDayMark.DONE
            active.any { it.hasProgress || it.isPartial } -> WeekDayMark.PARTIAL
            planned && dayLogs.none { it.cancelled && it.sessionId != null } -> WeekDayMark.PLANNED
            else -> WeekDayMark.EMPTY
        }
    }

    private fun setExerciseDone(ex: ExerciseDraft, done: Boolean) {
        ex.done = done
        ex.sets.forEach { it.done = done }
    }
}
