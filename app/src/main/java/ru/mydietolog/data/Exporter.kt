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
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DaySummary(
    val date: LocalDate,
    val meals: List<Meal>,
    val workouts: List<WorkoutLog> = emptyList(),
    val marks: DayMarks? = null,
) {
    val photoCount get() = meals.sumOf { it.photoCount }
    val missingMain: List<MealType>
        get() = MealType.main.filter { t -> meals.none { it.type == t } }
    val hasMarks: Boolean get() = marks?.isEmpty == false
    val hasContent: Boolean get() = meals.isNotEmpty() || workouts.isNotEmpty() || hasMarks
}

data class WeekSummary(val start: LocalDate, val days: List<DaySummary>) {
    val end: LocalDate get() = start.plusDays(6)
    val mealCount get() = days.sumOf { it.meals.size }
    val photoCount get() = days.sumOf { it.photoCount }
    val workoutCount get() = days.sumOf { it.workouts.size }

    val hungerAverage: Average? get() = days.map { it.marks?.hunger }.averageOrNull()
    val stressAverage: Average? get() = days.map { it.marks?.stress }.averageOrNull()
    val menstruationDays: Int get() = days.count { it.marks?.menstruation == true }

    /** Дни, которые уже наступили (или сегодня) и в которых нет завтрака, обеда или ужина. */
    fun incompleteDays(today: LocalDate): List<DaySummary> =
        days.filter { !it.date.isAfter(today) && it.missingMain.isNotEmpty() }

    companion object {
        fun build(
            start: LocalDate,
            meals: List<Meal>,
            workouts: List<WorkoutLog> = emptyList(),
            marks: List<DayMarks> = emptyList(),
        ): WeekSummary = WeekSummary(
            start,
            (0..6).map { i ->
                val d = start.plusDays(i.toLong())
                DaySummary(
                    date = d,
                    meals = meals.filter { it.date == d }.sortedWith(Repository.mealOrder),
                    workouts = workouts.filter { it.date == d }.sortedBy { it.createdAt },
                    marks = marks.firstOrNull { it.date == d },
                )
            },
        )
    }
}

/**
 * Собирает ZIP за неделю:
 *  diary.md   — питание и факты тренировок по дням;
 *  diary.json — те же данные в структурированном виде;
 *  photos/    — фото, имена вида 2026-11-15_lunch_d1_p1.jpg.
 */
class Exporter(private val context: Context, private val repo: Repository) {

    fun fileName(week: WeekSummary) = "MyDietolog_${week.start}_${week.end}.zip"

    suspend fun buildZip(week: WeekSummary, today: LocalDate): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val zip = File(dir, fileName(week))

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
        appendLine("# Дневник: ${Ru.weekRange(week.start)}")
        appendLine()
        appendLine(
            "Приёмов пищи: ${week.mealCount}, фото: ${week.photoCount}, " +
                "тренировок: ${week.workoutCount}.",
        )
        weekMarksSummary(week)?.let { appendLine(it) }
        appendLine("Калории в этом файле — оценка пользователя, не измерение.")
        appendLine()
        week.days.forEach { day ->
            appendLine("## ${Ru.weekdayDayMonth(day.date)} (${day.date})")
            when {
                day.date.isAfter(today) -> appendLine("_День ещё не наступил._")
                !day.hasContent -> appendLine("_Нет записей._")
                day.missingMain.isNotEmpty() && day.meals.isNotEmpty() ->
                    appendLine("_Неполный день питания: нет ${day.missingMain.joinToString(", ") { it.genitive }}._")
                day.missingMain.isNotEmpty() && day.meals.isEmpty() && day.workouts.isNotEmpty() ->
                    appendLine("_Питание не заполнено; есть факт тренировки._")
            }
            val dayKcal = day.meals.mapNotNull { it.totalKcal }
            if (dayKcal.isNotEmpty()) appendLine("Оценка за день (сумма указанного): ${dayKcal.sum()} ккал")
            day.marks?.takeIf { !it.isEmpty }?.let { appendLine("Отметки: ${marksLine(it)}") }
            appendLine()

            if (day.meals.isNotEmpty()) {
                appendLine("### Питание")
                day.meals.forEach { meal ->
                    val head = buildList {
                        add(meal.type.title)
                        meal.time?.let { add(it) }
                        meal.totalKcal?.let { add("моя оценка: $it ккал") }
                    }.joinToString(" · ")
                    appendLine("#### $head")
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

            if (day.workouts.isNotEmpty()) {
                appendLine("### Тренировки")
                day.workouts.forEach { workout ->
                    appendWorkoutMarkdown(workout)
                }
            }
        }
    }

    private fun weekMarksSummary(week: WeekSummary): String? {
        val parts = buildList {
            week.hungerAverage?.let { add("голод — среднее ${formatAverage(it)} из ${DayMarks.SCALE_MAX} (дней: ${it.days})") }
            week.stressAverage?.let { add("стресс — среднее ${formatAverage(it)} из ${DayMarks.SCALE_MAX} (дней: ${it.days})") }
            if (week.menstruationDays > 0) add("месячные — ${week.menstruationDays} ${Ru.plural(week.menstruationDays, "день", "дня", "дней")}")
        }
        return if (parts.isEmpty()) null else "Отметки за неделю: ${parts.joinToString("; ")}."
    }

    private fun marksLine(marks: DayMarks): String = buildList {
        marks.menstruation?.let { add("месячные — ${if (it) "да" else "нет"}") }
        marks.hunger?.let { add("голод $it/${DayMarks.SCALE_MAX}") }
        marks.stress?.let { add("стресс $it/${DayMarks.SCALE_MAX}") }
    }.joinToString(" · ")

    private fun formatAverage(a: Average): String = String.format(Locale.forLanguageTag("ru"), "%.1f", a.value)

    private fun roundOne(value: Double): Double = Math.round(value * 10) / 10.0

    private fun StringBuilder.appendWorkoutMarkdown(workout: WorkoutLog) {
        val head = buildList {
            add(workout.sessionName)
            add(workout.markLabel())
            if (workout.sessionId == null) add("вне плана")
        }.joinToString(" · ")
        appendLine("#### $head")
        if (workout.exercises.isEmpty()) {
            appendLine("_Нет упражнений._")
            appendLine()
            return
        }
        workout.exercises.forEachIndexed { i, ex ->
            val status = if (ex.done) "✓" else "○"
            val tags = buildList {
                if (ex.isCustom) add("вне плана")
                if (ex.target.isNotBlank()) add("цель: ${ex.target}")
            }.joinToString(" · ")
            appendLine("${i + 1}. $status **${ex.name}**" + if (tags.isNotBlank()) " ($tags)" else "")
            if (ex.note.isNotBlank()) appendLine("   - заметка: ${ex.note}")
            when (ex.kind) {
                ExerciseKind.CARDIO -> {
                    ex.minutes?.let { appendLine("   - минуты: $it") }
                }
                ExerciseKind.STRENGTH -> {
                    val sets = ex.sets.filter { it.done || it.reps != null || it.weightKg != null }
                    if (sets.isNotEmpty()) {
                        val lines = sets.mapIndexed { si, s ->
                            val w = s.weightKg?.let { "${formatWeight(it)} кг" } ?: "—"
                            val r = s.reps?.toString() ?: "—"
                            val mark = if (s.done) "✓" else "○"
                            "${si + 1}) $w × $r $mark"
                        }
                        appendLine("   - подходы: ${lines.joinToString("; ")}")
                    }
                }
            }
        }
        appendLine()
    }

    private fun json(week: WeekSummary, today: LocalDate, photoNames: Map<String, String>): JSONObject =
        JSONObject().apply {
            put("app", "MyDietolog")
            put("format_version", 3)
            put("week_start", week.start.toString())
            put("week_end", week.end.toString())
            put("note", "kcal — оценка пользователя; workouts — факты тренировок; marks — отметки дня (шкала 0–10)")
            put("marks_summary", JSONObject().apply {
                put("hunger_average", week.hungerAverage?.let { roundOne(it.value) } ?: JSONObject.NULL)
                put("hunger_days", week.hungerAverage?.days ?: 0)
                put("stress_average", week.stressAverage?.let { roundOne(it.value) } ?: JSONObject.NULL)
                put("stress_days", week.stressAverage?.days ?: 0)
                put("menstruation_days", week.menstruationDays)
            })
            put("days", JSONArray(week.days.map { day ->
                JSONObject().apply {
                    put("date", day.date.toString())
                    put("future", day.date.isAfter(today))
                    put("incomplete", !day.date.isAfter(today) && day.missingMain.isNotEmpty())
                    put("missing", JSONArray(day.missingMain.map { it.code }))
                    put("marks", day.marks?.takeIf { !it.isEmpty }?.let { m ->
                        JSONObject().apply {
                            put("menstruation", m.menstruation ?: JSONObject.NULL)
                            put("hunger", m.hunger ?: JSONObject.NULL)
                            put("stress", m.stress ?: JSONObject.NULL)
                        }
                    } ?: JSONObject.NULL)
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
                    put("workouts", JSONArray(day.workouts.map { it.toExportJson() }))
                }
            }))
        }
}
