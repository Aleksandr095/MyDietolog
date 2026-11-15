package ru.mydietolog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
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
import java.util.UUID

/**
 * Хранит все записи в одном JSON-файле во внутренней памяти приложения,
 * фото — в папке photos/. Ничего не уходит в интернет.
 */
class Repository(private val context: Context) {

    private val dataFile = File(context.filesDir, "meals.json")
    val photosDir: File = File(context.filesDir, "photos").apply { mkdirs() }

    private val _meals = MutableStateFlow<List<Meal>>(emptyList())
    val meals: StateFlow<List<Meal>> = _meals.asStateFlow()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        val list = runCatching {
            if (!dataFile.exists()) emptyList()
            else {
                val root = JSONObject(dataFile.readText())
                val arr = root.optJSONArray("meals") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { Meal.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            }
        }.getOrElse { emptyList() }
        _meals.value = list
        cleanupOrphanPhotos(list)
    }

    fun mealsOn(date: LocalDate): List<Meal> =
        _meals.value.filter { it.date == date }.sortedWith(mealOrder)

    suspend fun upsert(meal: Meal) {
        val cleaned = meal.copy(dishes = meal.dishes.filterNot { it.isEmpty })
        if (cleaned.dishes.isEmpty() && cleaned.time.isNullOrBlank()) {
            delete(meal.id)
            return
        }
        val current = _meals.value
        val updated = if (current.any { it.id == cleaned.id }) {
            current.map { if (it.id == cleaned.id) cleaned else it }
        } else current + cleaned
        _meals.value = updated
        persist(updated)
    }

    suspend fun delete(mealId: String) {
        val current = _meals.value
        val meal = current.firstOrNull { it.id == mealId } ?: return
        val updated = current.filterNot { it.id == mealId }
        _meals.value = updated
        persist(updated)
        withContext(Dispatchers.IO) {
            meal.dishes.flatMap { it.photos }.forEach { File(photosDir, it).delete() }
        }
    }

    /** Копирует фото в память приложения, уменьшая до 1600 px по длинной стороне. */
    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val maxSide = maxOf(w, h)
                if (maxSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / maxSide
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val name = "${UUID.randomUUID()}.jpg"
            File(photosDir, name).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            name
        }.getOrNull()
    }

    fun photoFile(name: String): File = File(photosDir, name)

    /** Удаляет файл фото, если он больше нигде не используется в сохранённых записях. */
    suspend fun discardPhotoIfUnused(name: String) {
        val used = _meals.value.any { m -> m.dishes.any { name in it.photos } }
        if (!used) withContext(Dispatchers.IO) { File(photosDir, name).delete() }
    }

    private suspend fun persist(list: List<Meal>): Unit = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 1)
            put("meals", JSONArray(list.map { it.toJson() }))
        }
        val tmp = File(context.filesDir, "meals.json.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(root.toString())
            tmp.delete()
        }
        Unit
    }

    private fun cleanupOrphanPhotos(list: List<Meal>) {
        val used = list.flatMap { m -> m.dishes.flatMap { it.photos } }.toSet()
        photosDir.listFiles()?.forEach { f -> if (f.name !in used) f.delete() }
    }

    companion object {
        private const val MAX_SIDE = 1600

        val mealOrder: Comparator<Meal> = compareBy<Meal>({ it.type.ordinal }, { it.time ?: "99:99" }, { it.createdAt })
    }
}
