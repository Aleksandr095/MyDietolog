package ru.mydietolog.data

import org.json.JSONObject
import java.time.LocalDate

/** Вес, замеры и доза Семавика за день. Все числа необязательные. */
data class BodyLog(
    val date: LocalDate,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val chestCm: Double? = null,
    val thighCm: Double? = null,
    val armCm: Double? = null,
    val semavikMg: Double? = null,
) {
    val isEmpty: Boolean
        get() = weightKg == null && waistCm == null && hipsCm == null && chestCm == null &&
            thighCm == null && armCm == null && semavikMg == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date.toString())
        putNumber("weight_kg", weightKg)
        putNumber("waist_cm", waistCm)
        putNumber("hips_cm", hipsCm)
        putNumber("chest_cm", chestCm)
        putNumber("thigh_cm", thighCm)
        putNumber("arm_cm", armCm)
        putNumber("semavik_mg", semavikMg)
    }

    companion object {
        fun fromJson(o: JSONObject): BodyLog = BodyLog(
            date = LocalDate.parse(o.getString("date")),
            weightKg = o.optDoubleOrNull("weight_kg"),
            waistCm = o.optDoubleOrNull("waist_cm"),
            hipsCm = o.optDoubleOrNull("hips_cm"),
            chestCm = o.optDoubleOrNull("chest_cm"),
            thighCm = o.optDoubleOrNull("thigh_cm"),
            armCm = o.optDoubleOrNull("arm_cm"),
            semavikMg = o.optDoubleOrNull("semavik_mg"),
        )
    }
}

private fun JSONObject.putNumber(key: String, value: Double?) {
    if (value == null) return
    if (value % 1.0 == 0.0 && value <= Long.MAX_VALUE.toDouble()) put(key, value.toLong())
    else put(key, value)
}
