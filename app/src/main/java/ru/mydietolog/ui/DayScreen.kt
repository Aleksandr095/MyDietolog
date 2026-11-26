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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val bodyLog by vm.bodyLog.collectAsState()
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
            RoundIcon(AppIcons.settings, "Настройки", { vm.push(Screen.Settings) }, outlined = true)
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

        val circleOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        var pickMeals by remember { mutableStateOf<List<Meal>?>(null) }
        fun openType(type: MealType) {
            val ofType = dayMeals.filter { it.type == type }
            when {
                ofType.isEmpty() -> vm.openNewMeal(type)
                ofType.size == 1 -> vm.openMeal(ofType.first())
                else -> pickMeals = ofType
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "meal_circles_$date") {
                MealCircleRow(
                    meals = dayMeals,
                    order = circleOrder,
                    onOpen = { openType(it) },
                    onAdd = { openType(it) },
                )
            }
            item(key = "day_marks_$date") {
                DayMarksCard(
                    date = date,
                    marks = dayMarks,
                    onChange = { change -> vm.updateMarks(date, change) },
                    onMenstruation = vm::setMenstruation,
                )
            }
            item(key = "body_log_$date") {
                BodyLogCard(
                    selectedDate = date,
                    entries = bodyLog,
                    onSaveWeight = vm::saveWeight,
                    onSaveMeasurements = vm::saveMeasurements,
                    onSaveSemavik = vm::saveSemavik,
                )
            }
        }

        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
            PrimaryButton(
                "Выгрузить неделю",
                onClick = { vm.push(Screen.Export(weekStart)) },
                icon = AppIcons.download,
            )
        }
        pickMeals?.let { meals ->
            AlertDialog(
                onDismissRequest = { pickMeals = null },
                title = { Text(meals.first().type.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        meals.forEach { meal ->
                            val text = meal.dishes.map { it.description }.filter { it.isNotBlank() }.joinToString("; ")
                            TextButton(onClick = {
                                pickMeals = null
                                vm.openMeal(meal)
                            }) {
                                Text(
                                    text.ifBlank { "Без описания" } + if (meal.photoCount > 0) " · ${Ru.photos(meal.photoCount)}" else "",
                                    color = Palette.ink,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { pickMeals = null }) { Text("Закрыть") }
                },
            )
        }
    }
}

@Composable
private fun MealCircleRow(
    meals: List<Meal>,
    order: List<MealType>,
    onOpen: (MealType) -> Unit,
    onAdd: (MealType) -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        order.forEach { type ->
            val ofType = meals.filter { it.type == type }
            MealCircle(
                type = type,
                meals = ofType,
                onOpen = { onOpen(type) },
                onAdd = { onAdd(type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MealCircle(
    type: MealType,
    meals: List<Meal>,
    onOpen: (Meal) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier,
) {
    val meal = meals.firstOrNull()
    val caption = when {
        meal == null -> "Добавить"
        meals.size > 1 -> "${meals.size} ${Ru.plural(meals.size, "запись", "записи", "записей")}"
        else -> meal.dishes.map { it.description }.firstOrNull { it.isNotBlank() }?.let { line ->
            if (line.length > 18) line.take(17) + "…" else line
        } ?: meal.totalKcal?.let { "$it ккал" } ?: "Открыть"
    }
    Column(
        modifier.clickable { if (meal == null) onAdd() else onOpen(meal) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(68.dp)
                    .then(if (meal == null) Modifier else Modifier.border(1.5.dp, Palette.accent, CircleShape))
                    .background(if (meal == null) Palette.placeholder else Palette.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (meal != null) {
                    Icon(AppIcons.check, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1D1B))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.add, contentDescription = "Добавить ${type.title}", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            type.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.ink,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
        )
        Text(
            caption,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

