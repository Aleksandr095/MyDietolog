package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.ProgramSession
import ru.mydietolog.data.Ru
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.WorkoutProgram
import ru.mydietolog.data.movedFrom
import ru.mydietolog.data.sessionOn
import ru.mydietolog.data.weekStart
import java.time.LocalDate

@Composable
fun WorkoutsHomeScreen(vm: AppViewModel) {
    val program by vm.workouts.program.collectAsState()
    val logs by vm.workouts.logs.collectAsState()
    val moves by vm.workouts.moves.collectAsState()
    val today = vm.today()
    val selected = vm.workouts.selectedDate
    val weekStart = selected.weekStart()
    var showMove by remember { mutableStateOf(false) }

    val selectedSession = program?.let { sessionOn(it, moves, selected) }
    val dayLogs = logs.filter { it.date == selected }
    val logForSession = selectedSession?.let { s -> dayLogs.firstOrNull { it.sessionId == s.id } }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MyDietolog", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
                Text("Тренировки", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
                Text(
                    if (program != null) "${Ru.weekRange(weekStart)} · ${program!!.title}"
                    else "Программа ещё не загружена",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.muted,
                )
            }
            TextButton(onClick = { vm.workouts.openProgram() }) {
                Text("Программа", fontWeight = FontWeight.Bold, color = Palette.accent)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Текущая неделя", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
                    WorkoutWeekStrip(vm, weekStart, today, selected)
                }
            }

            item {
                SelectedDayCard(
                    date = selected,
                    today = today,
                    program = program,
                    session = selectedSession,
                    existing = logForSession,
                    movedFrom = selectedSession?.let { movedFrom(it, selected, moves) },
                    onSelectPlan = { vm.workouts.openPlan(selected) },
                    onMove = { showMove = true },
                    onStart = {
                        if (selectedSession != null) vm.workouts.startOrOpenSession(selected, selectedSession)
                        else vm.workouts.startOutsidePlan(selected)
                    },
                    onOpen = { log -> vm.workouts.openExisting(log) },
                    onLoadProgram = { vm.workouts.openProgram() },
                )
            }

            val otherLogs = dayLogs.filter { it.id != logForSession?.id }
            if (otherLogs.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ещё в этот день", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
                        otherLogs.forEach { log ->
                            val names = log.exercises.map { it.name }.filter { it.isNotBlank() }
                            ScheduleRow(
                                whenText = names.joinToString(", ").ifBlank { "Без упражнений" },
                                name = log.sessionName,
                                trailing = log.markLabel(),
                                onClick = { vm.workouts.openExisting(log) },
                            )
                        }
                    }
                }
            }

            item {
                SecondaryButton("История", onClick = { vm.push(Screen.WorkoutHistory) })
            }

            item {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(shape)
                        .dashedBorder(Palette.dashed, 14.dp)
                        .clickable { vm.workouts.startOutsidePlan(selected) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+ Тренировка вне плана", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
                }
            }
        }
        val currentProgram = program
        if (showMove && selectedSession != null && currentProgram != null) {
            MoveWorkoutDialog(
                sessionName = selectedSession.name,
                from = selected,
                occupied = { d -> sessionOn(currentProgram, moves, d)?.name },
                onPick = {
                    showMove = false
                    vm.workouts.moveSession(selected, it)
                },
                onDismiss = { showMove = false },
            )
        }
    }
}

@Composable
private fun SelectedDayCard(
    date: LocalDate,
    today: LocalDate,
    program: WorkoutProgram?,
    session: ProgramSession?,
    existing: WorkoutLog?,
    movedFrom: LocalDate?,
    onSelectPlan: () -> Unit,
    onMove: () -> Unit,
    onStart: () -> Unit,
    onOpen: (WorkoutLog) -> Unit,
    onLoadProgram: () -> Unit,
) {
    val dayTitle = when (date) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        today.minusDays(1) -> "Вчера"
        else -> Ru.weekdaysFull[date.dayOfWeek.value - 1]
    }
    val isFuture = date.isAfter(today)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            program == null -> {
                Text("Нет программы", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
                Text(
                    "Загрузите JSON-файл программы, чтобы видеть план на неделю.",
                    fontSize = 13.sp,
                    color = Palette.muted,
                )
                PrimaryButton("Открыть программу", onClick = onLoadProgram)
            }
            session == null -> {
                Text("$dayTitle · отдых", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
                Text(
                    "${Ru.dayMonth(date)} · в программе нет занятия на этот день.",
                    fontSize = 13.sp,
                    color = Palette.muted,
                )
                if (!isFuture) SecondaryButton("Тренировка вне плана", onClick = onStart)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$dayTitle · ${session.name}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.ink,
                        modifier = Modifier.weight(1f),
                    )
                    MetaChip(if (existing != null) existing.markLabel() else "план")
                }
                Text(
                    buildString {
                        append(Ru.dayMonth(date))
                        append(" · ")
                        append("${session.exercises.size} ${Ru.plural(session.exercises.size, "упражнение", "упражнения", "упражнений")}")
                        existing?.let { append(" · ${it.markLabel()}") }
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.muted,
                )
                if (movedFrom != null) {
                    Text(
                        "Перенесено с ${Ru.weekdayDayMonth(movedFrom)}. В программе день не меняется.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.muted,
                    )
                }
                when {
                    existing != null ->
                        PrimaryButton("Продолжить тренировку", onClick = { onOpen(existing) })
                    isFuture ->
                        InfoBanner("Тренировку можно начать, когда наступит этот день.")
                    else -> PrimaryButton("Начать", onClick = onStart)
                }
                SecondaryButton("Посмотреть план", onClick = onSelectPlan)
                SecondaryButton("Перенести", onClick = onMove)
            }
        }
    }
}

@Composable
private fun WorkoutWeekStrip(vm: AppViewModel, weekStart: LocalDate, today: LocalDate, selected: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = weekStart.plusDays(i.toLong())
            val sel = d == selected
            val isToday = d == today
            val hasSession = vm.workouts.sessionOn(d) != null
            val mark = vm.workouts.weekMark(d)
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(shape)
                    .background(
                        when {
                            sel -> Palette.accent
                            hasSession -> Palette.weekHighlight
                            else -> Palette.surface
                        },
                    )
                    .border(
                        width = if (isToday && !sel) 2.dp else 1.dp,
                        color = when {
                            sel -> Palette.accent
                            isToday -> Palette.accent
                            hasSession -> Palette.accentDark
                            else -> Palette.line
                        },
                        shape = shape,
                    )
                    .clickable { vm.workouts.selectDate(d) }
                    .semantics {
                        contentDescription = Ru.weekdayDayMonth(d)
                        this.selected = sel
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    d.dayOfMonth.toString(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (sel) Color.White else Palette.ink,
                )
                Text(
                    Ru.weekdaysShort[i],
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel) Color.White.copy(alpha = 0.85f) else Palette.muted,
                )
                Spacer(Modifier.height(4.dp))
                Dot(
                    on = mark == WeekDayMark.DONE || mark == WeekDayMark.PARTIAL || mark == WeekDayMark.PLANNED,
                    onColor = when {
                        sel -> Color.White
                        mark == WeekDayMark.DONE -> Palette.accent
                        mark == WeekDayMark.PARTIAL -> Palette.accentDark
                        else -> Palette.dotOff
                    },
                    offColor = if (sel) Color.White.copy(alpha = 0.3f) else Palette.dotOff,
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    whenText: String,
    name: String,
    trailing: String,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(whenText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
        }
        MetaChip(trailing)
    }
}

@Composable
fun MetaChip(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Palette.accentDark,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.accentSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
internal fun MoveWorkoutDialog(
    sessionName: String,
    from: LocalDate,
    occupied: (LocalDate) -> String?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val week = from.weekStart()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text("Перенести «$sessionName»", fontWeight = FontWeight.Bold, color = Palette.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Только на эту неделю. На следующей неделе занятие снова в свой день.",
                    fontSize = 13.sp,
                    color = Palette.muted,
                )
                (0..6).map { week.plusDays(it.toLong()) }.filter { it != from }.forEach { day ->
                    val other = occupied(day)
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(1.dp, Palette.line, shape)
                            .clickable { onPick(day) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(Ru.weekdayDayMonth(day), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
                        Text(
                            if (other == null) "День отдыха" else "Поменяются местами с «$other»",
                            fontSize = 12.sp,
                            color = Palette.muted,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = Palette.muted) } },
    )
}

