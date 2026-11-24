package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.Ru
import ru.mydietolog.data.weekStart
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthScreen(vm: AppViewModel, initial: YearMonth) {
    val all by vm.meals.collectAsState()
    var month by remember { mutableStateOf(initial) }
    val today = vm.today()
    val focus = if (vm.tab == MainTab.WORKOUTS) vm.workouts.selectedDate else vm.selectedDate
    val selWeek = focus.weekStart()

    val first = month.atDay(1)
    val gridStart = first.weekStart()
    val last = month.atEndOfMonth()
    val weeks = generateSequence(gridStart) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(title = Ru.monthYear(first), onBack = { vm.back() }) {
            RoundIcon(AppIcons.left, "Предыдущий месяц", { month = month.minusMonths(1) })
            RoundIcon(AppIcons.right, "Следующий месяц", { month = month.plusMonths(1) })
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Ru.weekdaysShort.forEach {
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.muted,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            weeks.forEach { ws ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (ws == selWeek) Palette.weekHighlight else Color.Transparent)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (0..6).forEach { i ->
                        val d = ws.plusDays(i.toLong())
                        if (d.month != month.month || d.year != month.year) {
                            Spacer(Modifier.weight(1f).height(58.dp))
                        } else {
                            DayCell(
                                date = d,
                                dots = dotsFor(all, d),
                                isToday = d == today,
                                future = d.isAfter(today),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (vm.tab == MainTab.WORKOUTS) {
                                    vm.workouts.selectDate(d)
                                    vm.back()
                                } else {
                                    vm.openDay(d)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.padding(start = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Dot(true, size = 6.dp); Dot(true, size = 6.dp); Dot(false, size = 6.dp); Dot(true, size = 6.dp)
                }
                Text(
                    "Завтрак · Обед · Ужин · Перекус; серая — нет записи",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.muted,
                )
            }
        }

        val week = vm.weekSummary(selWeek)
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Palette.surface)
                .border(1.dp, Palette.line, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Неделя ${Ru.weekRange(selWeek)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(Ru.meals(week.mealCount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
            }
            PrimaryButton("Выгрузить эту неделю", onClick = { vm.push(Screen.Export(selWeek)) }, icon = AppIcons.download, dark = true)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    dots: List<Boolean>,
    isToday: Boolean,
    future: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .height(58.dp)
            .clip(shape)
            .background(Palette.surface)
            .border(if (isToday) 2.dp else 1.dp, if (isToday) Palette.accent else Palette.line, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = Ru.dayMonth(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (future) Color(0xFF7A817C) else Palette.ink,
        )
        Box(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { dots.forEach { Dot(it) } }
    }
}
