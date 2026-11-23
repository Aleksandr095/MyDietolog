package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.Meal
import ru.mydietolog.data.MealType
import ru.mydietolog.data.Ru
import ru.mydietolog.data.weekStart
import java.time.LocalDate
import java.time.YearMonth

/** Точки З·О·У·П для дня. */
fun dotsFor(meals: List<Meal>, date: LocalDate): List<Boolean> =
    MealType.entries.map { t -> meals.any { it.date == date && it.type == t } }

@Composable
fun DayScreen(vm: AppViewModel) {
    val all by vm.meals.collectAsState()
    val allMarks by vm.marks.collectAsState()
    val date = vm.selectedDate
    val weekStart = date.weekStart()
    val dayMeals = all.filter { it.date == date }.sortedWith(ru.mydietolog.data.Repository.mealOrder)
    val dayMarks = allMarks.firstOrNull { it.date == date }
    val filled = MealType.main.count { t -> dayMeals.any { it.type == t } } + if (dayMeals.any { it.type == MealType.SNACK }) 1 else 0

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        // Шапка
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MyDietolog", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
                Text(Ru.monthYear(date), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
            }
            RoundIcon(AppIcons.left, "Предыдущая неделя", { vm.selectedDate = date.minusWeeks(1) })
            RoundIcon(AppIcons.right, "Следующая неделя", { vm.selectedDate = date.plusWeeks(1) })
            RoundIcon(AppIcons.calendar, "Календарь месяца", { vm.push(Screen.Month(YearMonth.from(date))) }, outlined = true)
        }

        // Неделя
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0..6).forEach { i ->
                val d = weekStart.plusDays(i.toLong())
                val sel = d == date
                val isToday = d == vm.today()
                val dots = dotsFor(all, d)
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(74.dp)
                        .clip(shape)
                        .background(if (sel) Palette.accent else Palette.surface)
                        .border(
                            width = if (isToday && !sel) 2.dp else 1.dp,
                            color = if (sel) Palette.accent else if (isToday) Palette.accent else Palette.line,
                            shape = shape,
                        )
                        .clickable { vm.selectedDate = d }
                        .semantics {
                            contentDescription = Ru.weekdayDayMonth(d)
                            selected = sel
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        Ru.weekdaysShort[i],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sel) Color.White else Palette.muted,
                    )
                    Text(
                        d.dayOfMonth.toString(),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (sel) Color.White else Palette.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        dots.forEach { on ->
                            Dot(
                                on,
                                onColor = if (sel) Color.White else Palette.accent,
                                offColor = if (sel) Color.White.copy(alpha = 0.3f) else Palette.dotOff,
                            )
                        }
                    }
                }
            }
        }

        // Заголовок дня
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                Ru.weekdayDayMonth(date),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text("$filled из 4", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        }

        // Приёмы пищи
        val rows = buildList {
            MealType.entries.forEach { t ->
                val ofType = dayMeals.filter { it.type == t }
                ofType.forEach { add(it to t) }
                if (ofType.isEmpty() || t == MealType.SNACK) add(null to t)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "day_marks_$date") {
                DayMarksCard(dayMarks) { change -> vm.updateMarks(date, change) }
            }
            items(rows, key = { (m, t) -> m?.id ?: "empty_${t.code}" }) { (meal, type) ->
                if (meal != null) MealCard(vm, meal) { vm.openMeal(meal) }
                else EmptyMealCard(type) { vm.openNewMeal(type) }
            }
        }

        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
            PrimaryButton(
                "Выгрузить неделю",
                onClick = { vm.push(Screen.Export(weekStart)) },
                icon = AppIcons.download,
                dark = true,
            )
        }
    }
}

@Composable
private fun MealCard(vm: AppViewModel, meal: Meal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val firstPhoto = meal.dishes.flatMap { it.photos }.firstOrNull()
    val text = meal.dishes.map { it.description }.filter { it.isNotBlank() }.joinToString("; ")
    val meta = buildList {
        if (meal.photoCount > 0) add(Ru.photos(meal.photoCount))
        val w = meal.dishes.mapNotNull { it.weightG }
        if (w.isNotEmpty()) add("${w.sum()} г")
        meal.totalKcal?.let { add("моя оценка $it ккал") }
    }.joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoThumb(
            file = firstPhoto?.let { vm.photoFile(it) },
            modifier = Modifier.size(76.dp),
            radius = 14.dp,
            description = meal.type.title,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.type.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink, modifier = Modifier.weight(1f))
                meal.time?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted) }
            }
            Text(
                text.ifBlank { "Без описания" },
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = if (text.isBlank()) Palette.muted else Palette.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) Text(meta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        }
    }
}

@Composable
private fun EmptyMealCard(type: MealType, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .dashedBorder(Palette.field, 20.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.add, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
        }
        Text(
            type.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.ink,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Text("добавить", fontSize = 13.sp, color = Palette.muted)
    }
}
