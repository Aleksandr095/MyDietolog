package ru.mydietolog.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DaySummary(val date: LocalDate, val meals: List<Meal>) {
    val photoCount get() = meals.sumOf { it.photoCount }
    val missingMain: List<MealType>
        get() = MealType.main.filter { t -> meals.none { it.type == t } }
}

data class WeekSummary(val start: LocalDate, val days: List<DaySummary>) {
    val end: LocalDate get() = start.plusDays(6)
    val mealCount get() = days.sumOf { it.meals.size }
    val photoCount get() = days.sumOf { it.photoCount }

    /** Дни, которые уже наступили (или сегодня) и в которых нет завтрака, обеда или ужина. */
    fun incompleteDays(today: LocalDate): List<DaySummary> =
        days.filter { !it.date.isAfter(today) && it.missingMain.isNotEmpty() }

    companion object {
        fun build(start: LocalDate, all: List<Meal>): WeekSummary = WeekSummary(
            start,
            (0..6).map { i ->
                val d = start.plusDays(i.toLong())
                DaySummary(d, all.filter { it.date == d }.sortedWith(Repository.mealOrder))
            },
        )
    }
}

/**
 * Собирает ZIP за неделю:
 *  diary.md   — читаемое описание по дням;
 *  diary.json — те же данные в структурированном виде;
 *  photos/    — фото, имена вида 2026-11-15_lunch_d1_p1.jpg.
 */
class Exporter(private val context: Context, private val repo: Repository) {

    fun fileName(week: WeekSummary) = "MyDietolog_${week.start}_${week.end}.zip"

    suspend fun buildZip(week: WeekSummary, today: LocalDate): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val zip = File(dir, fileName(week))

        // Имена фото в архиве
        val photoNames = mutableMapOf<String, String>()
        week.days.forEach { day ->
            val typeCounters = mutableMapOf<MealType, Int>()
            day.meals.forEach { meal ->
                val n = (typeCounters[meal.type] ?: 0) + 1
                typeCounters[meal.type] = n
                val mealTag = if (meal.type == MealType.SNACK) "${meal.type.code}$n" else meal.type.code
                meal.dishes.forEachIndexed { di, dish ->
                    dish.photos.forEachIndexed { pi, p ->
                        photoNames[p] = "photos/${day.date}_${mealTag}_d${di + 1}_p${pi + 1}.jpg"
                    }
                }
            }
        }

        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("diary.md"))
            zos.write(markdown(week, today, photoNames).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("diary.json"))
            zos.write(json(week, today, photoNames).toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            photoNames.forEach { (file, entry) ->
                val src = repo.photoFile(file)
                if (src.exists()) {
                    zos.putNextEntry(ZipEntry(entry))
                    src.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        zip
    }

    /** Копирует архив в «Загрузки». Возвращает true при успехе. */
    suspend fun saveToDownloads(zip: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zip.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyDietolog")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }

    private fun markdown(week: WeekSummary, today: LocalDate, photoNames: Map<String, String>): String = buildString {
        appendLine("# Дневник питания: ${Ru.weekRange(week.start)}")
        appendLine()
        appendLine("Приёмов пищи: ${week.mealCount}, фото: ${week.photoCount}.")
        appendLine("Калории в этом файле — оценка пользователя, не измерение.")
        appendLine()
        week.days.forEach { day ->
            appendLine("## ${Ru.weekdayDayMonth(day.date)} (${day.date})")
            when {
                day.date.isAfter(today) -> appendLine("_День ещё не наступил._")
                day.meals.isEmpty() -> appendLine("_Нет записей._")
                day.missingMain.isNotEmpty() ->
                    appendLine("_Неполный день: нет ${day.missingMain.joinToString(", ") { it.genitive }}._")
            }
            val dayKcal = day.meals.mapNotNull { it.totalKcal }
            if (dayKcal.isNotEmpty()) appendLine("Оценка за день (сумма указанного): ${dayKcal.sum()} ккал")
            appendLine()
            day.meals.forEach { meal ->
                val head = buildList {
                    add(meal.type.title)
                    meal.time?.let { add(it) }
                    meal.totalKcal?.let { add("моя оценка: $it ккал") }
                }.joinToString(" · ")
                appendLine("### $head")
                meal.dishes.forEachIndexed { i, dish ->
                    val parts = buildList {
                        add(dish.description.ifBlank { "(без описания)" })
                        dish.weightG?.let { add("$it г") }
                        dish.kcal?.let { add("$it ккал") }
                    }
                    appendLine("${i + 1}. ${parts.joinToString(" — ")}")
                    dish.photos.forEach { p -> photoNames[p]?.let { appendLine("   - фото: $it") } }
                }
                appendLine()
            }
        }
    }

    private fun json(week: WeekSummary, today: LocalDate, photoNames: Map<String, String>): JSONObject =
        JSONObject().apply {
            put("app", "MyDietolog")
            put("format_version", 1)
            put("week_start", week.start.toString())
            put("week_end", week.end.toString())
            put("note", "kcal — оценка пользователя")
            put("days", JSONArray(week.days.map { day ->
                JSONObject().apply {
                    put("date", day.date.toString())
                    put("future", day.date.isAfter(today))
                    put("incomplete", !day.date.isAfter(today) && day.missingMain.isNotEmpty())
                    put("missing", JSONArray(day.missingMain.map { it.code }))
                    put("meals", JSONArray(day.meals.map { meal ->
                        JSONObject().apply {
                            put("type", meal.type.code)
                            put("type_ru", meal.type.title)
                            put("time", meal.time ?: JSONObject.NULL)
                            put("total_kcal_user", meal.totalKcal ?: JSONObject.NULL)
                            put("dishes", JSONArray(meal.dishes.map { d ->
                                JSONObject().apply {
                                    put("description", d.description)
                                    put("weight_g", d.weightG ?: JSONObject.NULL)
                                    put("kcal_user", d.kcal ?: JSONObject.NULL)
                                    put("photos", JSONArray(d.photos.mapNotNull { photoNames[it] }))
                                }
                            }))
                        }
                    }))
                }
            }))
        }
}
