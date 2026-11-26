package ru.mydietolog.data

import org.json.JSONObject
import java.time.LocalDate

/** Отметки дня без привязки к приёмам пищи. Все поля необязательные: null — «не отмечено». */
data class DayMarks(
    val date: LocalDate,
    val menstruation: Boolean? = null,
    val hunger: Int? = null,
    val stress: Int? = null,
) {
    val isEmpty: Boolean get() = menstruation == null && hunger == null && stress == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date.toString())
        put("menstruation", menstruation ?: JSONObject.NULL)
        put("hunger", hunger ?: JSONObject.NULL)
        put("stress", stress ?: JSONObject.NULL)
    }

    companion object {
        const val SCALE_MAX = 10

        fun fromJson(o: JSONObject): DayMarks = DayMarks(
            date = LocalDate.parse(o.getString("date")),
            menstruation = if (o.isNull("menstruation")) null else o.optBoolean("menstruation"),
            hunger = o.optIntOrNull("hunger")?.coerceIn(0, SCALE_MAX),
            stress = o.optIntOrNull("stress")?.coerceIn(0, SCALE_MAX),
        )
    }
}

/** Среднее по непустым значениям и число дней, по которым оно посчитано. */
data class Average(val value: Double, val days: Int)

fun Iterable<Int?>.averageOrNull(): Average? {
    val values = mapNotNull { it }
    return if (values.isEmpty()) null else Average(values.average(), values.size)
}

/** Непрерывный участок с menstruation == true, в который входит [date], или null. */
fun menstruationSpanContaining(date: LocalDate, marks: List<DayMarks>): Pair<LocalDate, LocalDate>? {
    val byDate = marks.associate { it.date to it.menstruation }
    if (byDate[date] != true) return null
    var start = date
    while (byDate[start.minusDays(1)] == true) start = start.minusDays(1)
    var end = date
    while (byDate[end.plusDays(1)] == true) end = end.plusDays(1)
    return start to end
}
