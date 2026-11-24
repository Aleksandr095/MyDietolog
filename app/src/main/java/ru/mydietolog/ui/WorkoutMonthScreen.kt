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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.Ru
import ru.mydietolog.data.weekStart
import java.time.LocalDate
import java.time.YearMonth

/** Месяц занятий: план, частично и завершено. Выбор дня возвращает на вкладку тренировок. */
@Composable
fun WorkoutMonthScreen(vm: AppViewModel, initial: YearMonth) {
    var month by remember { mutableStateOf(initial) }
    val today = vm.today()
    val selected = vm.workouts.selectedDate
    val first = month.atDay(1)
    val gridStart = first.weekStart()
    val last = month.atEndOfMonth()
    val weeks = generateSequence(gridStart) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(title = Ru.monthYear(first), subtitle = "Тренировки", onBack = { vm.back() }) {
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
                        textAlign = TextAlign.Center,
                    )
                }
            }
            weeks.forEach { ws ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (0..6).forEach { i ->
                        val d = ws.plusDays(i.toLong())
                        if (d.month != month.month || d.year != month.year) {
                            Spacer(Modifier.weight(1f).height(58.dp))
                        } else {
                            WorkoutDayCell(
                                date = d,
                                mark = vm.workouts.weekMark(d),
                                selected = d == selected,
                                isToday = d == today,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    vm.workouts.selectDate(d)
                                    vm.back()
                                },
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.padding(start = 8.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendRow(Palette.weekHighlight, "Занятие в плане")
                LegendRow(Palette.accent, "Завершено")
                LegendRow(Palette.muted, "Частично")
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
    }
}

@Composable
private fun WorkoutDayCell(
    date: LocalDate,
    mark: WeekDayMark,
    selected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        selected -> Palette.accent
        mark != WeekDayMark.EMPTY -> Palette.weekHighlight
        else -> Palette.surface
    }
    Column(
        modifier
            .height(58.dp)
            .clip(shape)
            .background(background)
            .border(
                width = if (isToday && !selected) 2.dp else 1.dp,
                color = if (selected || isToday) Palette.accent else Palette.line,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = Ru.dayMonth(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Palette.ink,
        )
        Box(Modifier.height(6.dp))
        val dot = when (mark) {
            WeekDayMark.DONE -> if (selected) Color.White else Palette.accent
            WeekDayMark.PARTIAL -> if (selected) Color.White else Palette.muted
            WeekDayMark.PLANNED -> if (selected) Color.White.copy(alpha = 0.7f) else Palette.accentDark
            WeekDayMark.EMPTY -> null
        }
        if (dot != null) Dot(on = true, onColor = dot) else Box(Modifier.height(6.dp))
    }
}
