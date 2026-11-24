package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.Ru
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.weekStart
import java.time.YearMonth

@Composable
fun WorkoutHistoryScreen(vm: AppViewModel) {
    val logs by vm.workouts.logs.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val months = logs
        .sortedWith(compareByDescending<WorkoutLog> { it.date }.thenByDescending { it.createdAt })
        .groupBy { YearMonth.from(it.date) }
        .map { (month, monthLogs) ->
            month to monthLogs.groupBy { it.date.weekStart() }.toList()
        }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(title = "История", subtitle = "По месяцам и неделям", onBack = { vm.back() })
        if (months.isEmpty()) {
            Text(
                "Пока нет записанных занятий.",
                fontSize = 14.sp,
                color = Palette.muted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                months.forEach { (month, weeks) ->
                    item(key = "month-$month") {
                        Text(
                            Ru.monthYear(month.atDay(1)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.ink,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    weeks.forEach { (start, weekLogs) ->
                        item(key = "week-$month-$start") {
                            Text(
                                Ru.weekRange(start),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.muted,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            )
                        }
                        items(weekLogs, key = { it.id }) { log ->
                            HistoryRow(log) { vm.workouts.openExisting(log) }
                        }
                    }
                }
            }
            TextButton(
                onClick = { confirmClear = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                Text("Очистить историю", fontWeight = FontWeight.Bold, color = Palette.danger)
            }
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Очистить историю?") },
                text = { Text("Все записанные тренировки будут удалены.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        vm.workouts.clearHistory()
                    }) { Text("Очистить", color = Palette.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
                },
            )
        }
    }
}

@Composable
private fun HistoryRow(log: WorkoutLog, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val names = log.exercises.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Ru.weekdayDayMonth(log.date), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
            Text(log.sessionName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
            if (names.isNotBlank()) {
                Text(names, fontSize = 12.sp, color = Palette.muted)
            }
        }
        Text(log.markLabel(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.accentDark)
    }
}
