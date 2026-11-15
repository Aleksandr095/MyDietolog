package ru.mydietolog.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class MealType(val code: String, val title: String, val genitive: String) {
    BREAKFAST("breakfast", "Завтрак", "завтрака"),
    LUNCH("lunch", "Обед", "обеда"),
    DINNER("dinner", "Ужин", "ужина"),
    SNACK("snack", "Перекус", "перекуса");

    companion object {
        fun fromCode(code: String): MealType = entries.firstOrNull { it.code == code } ?: SNACK

        /** Приёмы, отсутствие которых делает день неполным. */
        val main = listOf(BREAKFAST, LUNCH, DINNER)
    }
}

const val MAX_PHOTOS_PER_DISH = 2

data class Dish(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val weightG: Int? = null,
    val kcal: Int? = null,
    /** Имена файлов в папке фото приложения, максимум 2. */
    val photos: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = description.isBlank() && weightG == null && kcal == null && photos.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("description", description)
        put("weight_g", weightG ?: JSONObject.NULL)
        put("kcal", kcal ?: JSONObject.NULL)
        put("photos", JSONArray(photos))
    }

    companion object {
        fun fromJson(o: JSONObject): Dish = Dish(
            id = o.optString("id", UUID.randomUUID().toString()),
            description = o.optString("description", ""),
            weightG = o.optIntOrNull("weight_g"),
            kcal = o.optIntOrNull("kcal"),
            photos = o.optJSONArray("photos")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                ?: emptyList(),
        )
    }
}

data class Meal(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val type: MealType,
    /** «ЧЧ:ММ» или null. */
    val time: String? = null,
    val dishes: List<Dish> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Сумма оценок блюд или null, если ни у одного блюда нет оценки. */
    val totalKcal: Int?
        get() {
            val values = dishes.mapNotNull { it.kcal }
            return if (values.isEmpty()) null else values.sum()
        }

    val photoCount: Int get() = dishes.sumOf { it.photos.size }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date.toString())
        put("type", type.code)
        put("time", time ?: JSONObject.NULL)
        put("created_at", createdAt)
        put("dishes", JSONArray(dishes.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): Meal = Meal(
            id = o.getString("id"),
            date = LocalDate.parse(o.getString("date")),
            type = MealType.fromCode(o.getString("type")),
            time = if (o.isNull("time")) null else o.optString("time").ifBlank { null },
            createdAt = o.optLong("created_at", 0L),
            dishes = o.optJSONArray("dishes")?.let { arr ->
                (0 until arr.length()).map { Dish.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
        )
    }
}

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)
