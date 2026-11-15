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
import ru.mydietolog.data.Exporter
import ru.mydietolog.data.MAX_PHOTOS_PER_DISH
import ru.mydietolog.data.Meal
import ru.mydietolog.data.MealType
import ru.mydietolog.data.Repository
import ru.mydietolog.data.WeekSummary
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

sealed interface Screen {
    data object Day : Screen
    data object Edit : Screen
    data class Month(val month: YearMonth) : Screen
    data class Export(val weekStart: LocalDate) : Screen
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
    private val exporter = Exporter(app, repo)

    val meals = repo.meals

    val stack = mutableStateListOf<Screen>(Screen.Day)
    val current: Screen get() = stack.last()

    var selectedDate by mutableStateOf(LocalDate.now())
    var draft by mutableStateOf<MealDraft?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Для какого блюда выбирается фото и куда камера сохраняет снимок (переживает пересоздание экрана). */
    var pendingDishId by mutableStateOf<String?>(null)
    var pendingCameraUri by mutableStateOf<Uri?>(null)

    init {
        viewModelScope.launch { repo.load() }
    }

    fun today(): LocalDate = LocalDate.now()

    // ---------- навигация ----------

    fun push(s: Screen) { stack.add(s) }

    fun back(): Boolean {
        if (current is Screen.Edit) { saveDraft(); return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }

    fun openDay(date: LocalDate) {
        selectedDate = date
        stack.clear()
        stack.add(Screen.Day)
    }

    // ---------- редактирование ----------

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
        if (current is Screen.Edit) stack.removeAt(stack.lastIndex)
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
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    fun photoFile(name: String): File = repo.photoFile(name)

    // ---------- выгрузка ----------

    fun weekSummary(start: LocalDate): WeekSummary = WeekSummary.build(start, meals.value)

    fun exportFileName(week: WeekSummary) = exporter.fileName(week)

    fun export(week: WeekSummary, onReady: (File) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                onReady(exporter.buildZip(week, today()))
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
                val zip = exporter.buildZip(week, today())
                onDone(exporter.saveToDownloads(zip))
            } finally {
                busy = false
            }
        }
    }
}
