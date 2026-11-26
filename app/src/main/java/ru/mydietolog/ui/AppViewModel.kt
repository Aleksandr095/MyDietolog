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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mydietolog.data.BodyLogRepository
import ru.mydietolog.data.DayMarks
import ru.mydietolog.data.DayMarksRepository
import ru.mydietolog.data.Dish
import ru.mydietolog.data.Exporter
import ru.mydietolog.data.MAX_PHOTOS_PER_DISH
import ru.mydietolog.data.Meal
import ru.mydietolog.data.MealType
import ru.mydietolog.data.Repository
import ru.mydietolog.data.ReminderSettings
import ru.mydietolog.data.ReminderSettingsRepository
import ru.mydietolog.data.WeekSummary
import ru.mydietolog.data.WorkoutRepository
import ru.mydietolog.reminders.ReminderScheduler
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
    data object Settings : Screen

    data object WorkoutsHome : Screen
    data object WorkoutSession : Screen
    data class WorkoutPlan(val date: LocalDate) : Screen
    data object WorkoutProgram : Screen
    data object WorkoutHistory : Screen
    data class WorkoutMonth(val month: YearMonth) : Screen
    data object ProgramEdit : Screen
    data object CustomExercise : Screen
}

class DishDraft(
    val id: String = UUID.randomUUID().toString(),
    description: String = "",
    weight: String = "",
    kcal: String = "",
    photoNames: List<String> = emptyList(),
) {
    var description by mutableStateOf(description)
    var weight by mutableStateOf(weight)
    var kcal by mutableStateOf(kcal)
    val photos: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(photoNames) }
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

class AppViewModel(app: Application) : AndroidViewModel(app), WorkoutNav {

    val repo = Repository(app)
    val workoutRepo = WorkoutRepository(app)
    val marksRepo = DayMarksRepository(app)
    val bodyLogRepo = BodyLogRepository(app)
    val reminderRepo = ReminderSettingsRepository(app)
    val themeStore = ThemeStore(app)
    private val exporter = Exporter(app, repo)

    val meals = repo.meals
    val marks = marksRepo.marks
    val bodyLog = bodyLogRepo.entries
    val reminders = reminderRepo.settings
    val theme = themeStore.choice
    val workouts = WorkoutController(workoutRepo, viewModelScope, this)

    var tab by mutableStateOf(MainTab.DIARY)

    private val diaryStack = mutableStateListOf<Screen>(Screen.Day)
    private val workoutStack = mutableStateListOf<Screen>(Screen.WorkoutsHome)

    override val current: Screen
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

    var busy by mutableStateOf(false)
        private set

    var pendingDishId by mutableStateOf<String?>(null)
    var pendingCameraUri by mutableStateOf<Uri?>(null)

    init {
        viewModelScope.launch {
            repo.load()
            workoutRepo.load()
            marksRepo.load()
            bodyLogRepo.load()
        }
        viewModelScope.launch {
            reminderRepo.load()
            withContext(Dispatchers.IO) { ReminderScheduler.rescheduleAll(app) }
        }
    }

    fun updateMarks(date: LocalDate, change: (DayMarks) -> DayMarks) {
        viewModelScope.launch { marksRepo.update(date, change) }
    }

    fun setMenstruation(from: LocalDate, to: LocalDate, value: Boolean?) {
        viewModelScope.launch { marksRepo.setMenstruation(from, to, value) }
    }

    fun saveWeight(date: LocalDate, weightKg: Double?) {
        viewModelScope.launch { bodyLogRepo.setWeight(date, weightKg) }
    }

    fun saveMeasurements(
        date: LocalDate,
        waistCm: Double?,
        hipsCm: Double?,
        chestCm: Double?,
        thighCm: Double?,
        armCm: Double?,
    ) {
        viewModelScope.launch {
            bodyLogRepo.setMeasurements(date, waistCm, hipsCm, chestCm, thighCm, armCm)
        }
    }

    fun saveSemavik(date: LocalDate, semavikMg: Double?) {
        viewModelScope.launch { bodyLogRepo.setSemavik(date, semavikMg) }
    }

    fun setTheme(value: AppTheme) { themeStore.set(value) }

    fun updateReminders(change: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch {
            reminderRepo.update(change)
            withContext(Dispatchers.IO) { ReminderScheduler.rescheduleAll(getApplication<Application>()) }
        }
    }

    override fun today(): LocalDate = LocalDate.now()

    fun selectTab(t: MainTab) {
        if (tab == t) return
        if (tab == MainTab.DIARY && current is Screen.Edit) saveDraft()
        if (tab == MainTab.WORKOUTS && current is Screen.WorkoutSession) workouts.finish()
        tab = t
    }

    override fun push(s: Screen) { activeStack.add(s) }

    override fun popOne() {
        if (activeStack.size > 1) activeStack.removeAt(activeStack.lastIndex)
    }

    override fun popToWorkoutsHome() {
        while (workoutStack.size > 1) workoutStack.removeAt(workoutStack.lastIndex)
    }

    fun back(): Boolean = when (current) {
        is Screen.Edit -> { saveDraft(); true }
        is Screen.WorkoutSession -> { workouts.cancel(); true }
        else -> {
            if (activeStack.size <= 1) false
            else {
                activeStack.removeAt(activeStack.lastIndex)
                true
            }
        }
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

    fun weekSummary(start: LocalDate): WeekSummary = freshWeek(start)

    fun exportFileName(week: WeekSummary) = exporter.fileName(week)

    fun export(week: WeekSummary, onReady: (File) -> Unit) = runExport { onReady(buildZip(week)) }

    fun exportToDownloads(week: WeekSummary, onDone: (Boolean) -> Unit) = runExport {
        onDone(exporter.saveToDownloads(buildZip(week)))
    }

    private fun freshWeek(start: LocalDate) =
        WeekSummary.build(start, meals.value, workouts.logs.value, marks.value)

    private suspend fun buildZip(week: WeekSummary) = exporter.buildZip(freshWeek(week.start), today())

    private fun runExport(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }
}
