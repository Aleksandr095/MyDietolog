package ru.mydietolog.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.mydietolog.data.Dish
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ExerciseLog
import ru.mydietolog.data.Exporter
import ru.mydietolog.data.MAX_PHOTOS_PER_DISH
import ru.mydietolog.data.Meal
import ru.mydietolog.data.MealType
import ru.mydietolog.data.ProgramSession
import ru.mydietolog.data.Repository
import ru.mydietolog.data.WeekSummary
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.WorkoutProgram
import ru.mydietolog.data.WorkoutRepository
import ru.mydietolog.data.weekStart
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class MainTab { DIARY, WORKOUTS }

sealed interface Screen {
    data object Day : Screen
    data object Edit : Screen
    data class Month(val month: YearMonth) : Screen
    data class Export(val weekStart: LocalDate) : Screen

    data object WorkoutsHome : Screen
    data object WorkoutSession : Screen
    data object WorkoutProgram : Screen
    data object CustomExercise : Screen
}

class DishDraft(
    val id: String = UUID.randomUUID().toString(),
    description: String = "",
    weight: String = "",
    kcal: String = "",
    photos: List<String> = emptyList(),
) {
    var description by mutableStateOf(description)
    var weight by mutableStateOf(weight)
    var kcal by mutableStateOf(kcal)
    val photos: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(photos) }
    var importing by mutableStateOf(false)

    val canAddPhoto: Boolean get() = photos.size < MAX_PHOTOS_PER_DISH

    fun toDish() = Dish(
        id = id,
        description = description.trim(),
        weightG = weight.toIntOrNull(),
        kcal = kcal.toIntOrNull(),
        photos = photos.toList(),
    )

    companion object {
        fun from(d: Dish) = DishDraft(d.id, d.description, d.weightG?.toString() ?: "", d.kcal?.toString() ?: "", d.photos)
    }
}

class MealDraft(
    val id: String,
    val date: LocalDate,
    type: MealType,
    time: String?,
    dishes: List<DishDraft>,
    val createdAt: Long,
    val isNew: Boolean,
    val originalPhotos: Set<String>,
) {
    var type by mutableStateOf(type)
    var time by mutableStateOf(time)
    val dishes: SnapshotStateList<DishDraft> = mutableStateListOf<DishDraft>().apply { addAll(dishes) }

    val totalKcal: Int?
        get() {
            val values = dishes.mapNotNull { it.kcal.toIntOrNull() }
            return if (values.isEmpty()) null else values.sum()
        }

    fun toMeal() = Meal(
        id = id,
        date = date,
        type = type,
        time = time?.ifBlank { null },
        dishes = dishes.map { it.toDish() },
        createdAt = createdAt,
    )

    fun allPhotos(): Set<String> = dishes.flatMap { it.photos }.toSet()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository(app)
    val workoutRepo = WorkoutRepository(app)
    private val exporter = Exporter(app, repo)

    val meals = repo.meals
    val program = workoutRepo.program
    val workoutLogs = workoutRepo.logs
    val customNames = workoutRepo.customNames

    var tab by mutableStateOf(MainTab.DIARY)

    private val diaryStack = mutableStateListOf<Screen>(Screen.Day)
    private val workoutStack = mutableStateListOf<Screen>(Screen.WorkoutsHome)

    val current: Screen
        get() = when (tab) {
            MainTab.DIARY -> diaryStack.last()
            MainTab.WORKOUTS -> workoutStack.last()
        }

    private val activeStack: SnapshotStateList<Screen>
        get() = when (tab) {
            MainTab.DIARY -> diaryStack
            MainTab.WORKOUTS -> workoutStack
        }

    var selectedDate by mutableStateOf(LocalDate.now())
    var draft by mutableStateOf<MealDraft?>(null)
        private set

    var workoutDraft by mutableStateOf<WorkoutDraft?>(null)
        private set

    var customName by mutableStateOf("")
    var customSets by mutableStateOf("3")
    var customWeight by mutableStateOf("")
    var customReps by mutableStateOf("10")

    var programPreview by mutableStateOf<WorkoutProgram?>(null)
        private set
    var programError by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var pendingDishId by mutableStateOf<String?>(null)
    var pendingCameraUri by mutableStateOf<Uri?>(null)

    init {
        viewModelScope.launch {
            repo.load()
            workoutRepo.load()
        }
    }

    fun today(): LocalDate = LocalDate.now()

    fun selectTab(t: MainTab) {
        if (tab == t) return
        if (tab == MainTab.DIARY && current is Screen.Edit) saveDraft()
        if (tab == MainTab.WORKOUTS && current is Screen.WorkoutSession) saveWorkoutDraft()
        tab = t
    }

    fun push(s: Screen) { activeStack.add(s) }

    fun back(): Boolean {
        when (current) {
            is Screen.Edit -> { saveDraft(); return true }
            is Screen.WorkoutSession -> { saveWorkoutDraft(); return true }
            else -> Unit
        }
        if (activeStack.size > 1) {
            activeStack.removeAt(activeStack.lastIndex)
            if (current !is Screen.CustomExercise) {
                // clear custom form leftovers when leaving
            }
            return true
        }
        return false
    }

    fun openDay(date: LocalDate) {
        selectedDate = date
        tab = MainTab.DIARY
        diaryStack.clear()
        diaryStack.add(Screen.Day)
    }

    // ---------- питание ----------

    fun openNewMeal(type: MealType) {
        draft = MealDraft(
            id = UUID.randomUUID().toString(),
            date = selectedDate,
            type = type,
            time = null,
            dishes = listOf(DishDraft()),
            createdAt = System.currentTimeMillis(),
            isNew = true,
            originalPhotos = emptySet(),
        )
        push(Screen.Edit)
    }

    fun openMeal(meal: Meal) {
        val dishes = meal.dishes.map { DishDraft.from(it) }.ifEmpty { listOf(DishDraft()) }
        draft = MealDraft(
            id = meal.id,
            date = meal.date,
            type = meal.type,
            time = meal.time,
            dishes = dishes,
            createdAt = meal.createdAt,
            isNew = false,
            originalPhotos = meal.dishes.flatMap { it.photos }.toSet(),
        )
        push(Screen.Edit)
    }

    fun saveDraft() {
        val d = draft ?: run { closeEdit(); return }
        viewModelScope.launch {
            repo.upsert(d.toMeal())
            (d.originalPhotos - d.allPhotos()).forEach { repo.discardPhotoIfUnused(it) }
            closeEdit()
        }
    }

    fun deleteDraftMeal() {
        val d = draft ?: return
        viewModelScope.launch {
            repo.delete(d.id)
            d.allPhotos().forEach { repo.discardPhotoIfUnused(it) }
            closeEdit()
        }
    }

    private fun closeEdit() {
        draft = null
        if (current is Screen.Edit) diaryStack.removeAt(diaryStack.lastIndex)
    }

    fun addDish() { draft?.dishes?.add(DishDraft()) }

    fun removeDish(dish: DishDraft) {
        val d = draft ?: return
        d.dishes.remove(dish)
        if (d.dishes.isEmpty()) d.dishes.add(DishDraft())
        dish.photos.filterNot { it in d.originalPhotos }.forEach { p ->
            viewModelScope.launch { repo.discardPhotoIfUnused(p) }
        }
    }

    fun removePhoto(dish: DishDraft, name: String) {
        dish.photos.remove(name)
        if (name !in (draft?.originalPhotos ?: emptySet())) {
            viewModelScope.launch { repo.discardPhotoIfUnused(name) }
        }
    }

    fun addPhoto(dish: DishDraft, uri: Uri) {
        if (!dish.canAddPhoto) return
        dish.importing = true
        viewModelScope.launch {
            val name = repo.importPhoto(uri)
            if (name != null && dish.canAddPhoto) dish.photos.add(name)
            dish.importing = false
        }
    }

    fun newCameraUri(): Uri {
        val application = getApplication<Application>()
        val dir = File(application.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(application, "${application.packageName}.files", file)
    }

    fun photoFile(name: String): File = repo.photoFile(name)

    fun weekSummary(start: LocalDate): WeekSummary =
        WeekSummary.build(start, meals.value, workoutLogs.value)

    fun exportFileName(week: WeekSummary) = exporter.fileName(week)

    fun export(week: WeekSummary, onReady: (File) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val full = WeekSummary.build(week.start, meals.value, workoutLogs.value)
                onReady(exporter.buildZip(full, today()))
            } finally {
                busy = false
            }
        }
    }

    fun exportToDownloads(week: WeekSummary, onDone: (Boolean) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val full = WeekSummary.build(week.start, meals.value, workoutLogs.value)
                val zip = exporter.buildZip(full, today())
                onDone(exporter.saveToDownloads(zip))
            } finally {
                busy = false
            }
        }
    }

    // ---------- тренировки ----------

    fun openProgram() {
        programPreview = null
        programError = null
        push(Screen.WorkoutProgram)
    }

    fun pickProgramFile(uri: Uri, displayName: String?) {
        programError = null
        viewModelScope.launch {
            try {
                programPreview = workoutRepo.parseProgram(uri, displayName)
            } catch (e: Exception) {
                programPreview = null
                programError = e.message ?: "Не удалось прочитать файл"
            }
        }
    }

    fun confirmReplaceProgram() {
        val preview = programPreview ?: return
        viewModelScope.launch {
            workoutRepo.replaceProgram(preview)
            programPreview = null
            programError = null
        }
    }

    fun clearProgramPreview() {
        programPreview = null
        programError = null
    }

    /** Старт или открытие факта на дату по плановой сессии. */
    fun startOrOpenSession(date: LocalDate, session: ProgramSession) {
        viewModelScope.launch {
            val existing = workoutRepo.logsOn(date).firstOrNull { it.sessionId == session.id }
            val log = existing ?: WorkoutLog.fromProgramSession(date, session)
            if (existing == null) workoutRepo.upsertLog(log)
            showWorkoutLog(log)
        }
    }

    fun startOutsidePlan(date: LocalDate = today()) {
        viewModelScope.launch {
            val log = WorkoutLog.outsidePlan(date)
            workoutRepo.upsertLog(log)
            showWorkoutLog(log)
        }
    }

    fun openExistingLog(log: WorkoutLog) {
        showWorkoutLog(log)
    }

    private fun showWorkoutLog(log: WorkoutLog) {
        val resolver = lastTimeResolver(workoutRepo::lastTimeFor, log.date)
        workoutDraft = WorkoutDraft.from(log, resolver)
        if (current !is Screen.WorkoutSession) push(Screen.WorkoutSession)
    }

    fun saveWorkoutDraft() {
        val d = workoutDraft ?: run { closeWorkoutSession(); return }
        viewModelScope.launch {
            workoutRepo.upsertLog(d.toLog())
            closeWorkoutSession()
        }
    }

    private fun closeWorkoutSession() {
        workoutDraft = null
        if (current is Screen.WorkoutSession) {
            // pop session and any custom on top was already handled
            while (workoutStack.isNotEmpty() && workoutStack.last() !is Screen.WorkoutsHome) {
                val last = workoutStack.last()
                workoutStack.removeAt(workoutStack.lastIndex)
                if (last is Screen.WorkoutSession) break
            }
        }
    }

    fun openCustomExercise() {
        customName = ""
        customSets = "3"
        customWeight = ""
        customReps = "10"
        push(Screen.CustomExercise)
    }

    fun nameSuggestions(): List<String> = workoutRepo.suggestNames(customName)

    fun applyNameSuggestion(name: String) {
        customName = name
    }

    fun addCustomExerciseToDraft() {
        val d = workoutDraft ?: return
        val name = customName.trim()
        if (name.isBlank()) return
        val setCount = customSets.toIntOrNull()?.coerceIn(1, 12) ?: 3
        val weight = customWeight.replace(',', '.')
        val reps = customReps
        val sets = List(setCount) {
            SetDraft(weight = weight, reps = reps, done = false)
        }
        d.exercises.add(
            ExerciseDraft(
                name = name,
                target = "$setCount × ${reps.ifBlank { "—" }}",
                kind = ExerciseKind.STRENGTH,
                isCustom = true,
                sets = sets,
                lastTimeLabel = ru.mydietolog.data.formatLastTime(
                    workoutRepo.lastTimeFor(null, name, d.date),
                ),
            ),
        )
        viewModelScope.launch {
            workoutRepo.rememberCustomName(name)
            workoutRepo.upsertLog(d.toLog())
        }
        if (current is Screen.CustomExercise) {
            workoutStack.removeAt(workoutStack.lastIndex)
        }
    }

    fun addSet(ex: ExerciseDraft) {
        val last = ex.sets.lastOrNull()
        ex.sets.add(SetDraft(weight = last?.weight.orEmpty(), reps = last?.reps.orEmpty()))
    }

    fun toggleExerciseDone(ex: ExerciseDraft) {
        ex.done = !ex.done
        if (ex.done && ex.kind == ExerciseKind.STRENGTH) {
            ex.sets.forEach { if (it.reps.isNotBlank() || it.weight.isNotBlank()) it.done = true }
        }
        persistWorkoutDraftQuiet()
    }

    fun persistWorkoutDraftQuiet() {
        val d = workoutDraft ?: return
        viewModelScope.launch { workoutRepo.upsertLog(d.toLog()) }
    }

    fun markAllPlannedDone() {
        val d = workoutDraft ?: return
        d.exercises.filterNot { it.isCustom }.forEach { ex ->
            ex.done = true
            if (ex.kind == ExerciseKind.STRENGTH) {
                ex.sets.forEach { s ->
                    if (s.reps.isNotBlank() || s.weight.isNotBlank()) s.done = true
                }
            }
        }
        persistWorkoutDraftQuiet()
    }

    fun weekMark(date: LocalDate): WeekDayMark {
        val logs = workoutRepo.logsOn(date)
        val prog = program.value?.sessionFor(date)
        return when {
            logs.any { it.isFullyDone } -> WeekDayMark.DONE
            logs.any { it.hasProgress || it.isPartial } -> WeekDayMark.PARTIAL
            prog != null -> WeekDayMark.PLANNED
            else -> WeekDayMark.EMPTY
        }
    }
}

enum class WeekDayMark { EMPTY, PLANNED, PARTIAL, DONE }
