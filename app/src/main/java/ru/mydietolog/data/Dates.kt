package ru.mydietolog.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object Ru {
    val monthsNominative = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    val weekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val weekdaysFull = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

    fun monthYear(d: LocalDate) = "${monthsNominative[d.monthValue - 1]} ${d.year}"
    fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${monthsGenitive[d.monthValue - 1]}"
    fun weekdayDayMonth(d: LocalDate) = "${weekdaysFull[d.dayOfWeek.value - 1]}, ${dayMonth(d)}"
    fun shortDay(d: LocalDate) = "${weekdaysShort[d.dayOfWeek.value - 1]}, ${d.dayOfMonth}"

    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) {
            "${start.dayOfMonth} – ${end.dayOfMonth} ${monthsGenitive[end.monthValue - 1]} ${end.year}"
        } else {
            "${dayMonth(start)} – ${dayMonth(end)} ${end.year}"
        }
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1 && n100 != 11 -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
    }

    fun meals(n: Int) = "$n ${plural(n, "приём", "приёма", "приёмов")}"
    fun photos(n: Int) = "$n фото"
}

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
