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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ProgramSession
import ru.mydietolog.data.Ru
import ru.mydietolog.data.WorkoutLog
import ru.mydietolog.data.WorkoutProgram
import ru.mydietolog.data.weekStart
import java.time.LocalDate

@Composable
fun WorkoutsHomeScreen(vm: AppViewModel) {
    val program by vm.workouts.program.collectAsState()
    val logs by vm.workouts.logs.collectAsState()
    val today = vm.today()
    val weekStart = today.weekStart()

    val todaySession = program?.sessionFor(today)
    val todayLogs = logs.filter { it.date == today }
    val todayLogForSession = todaySession?.let { s -> todayLogs.firstOrNull { it.sessionId == s.id } }

    val upcoming = (1..6).map { weekStart.plusDays(it.toLong()) }
        .filter { it.isAfter(today) }
        .mapNotNull { d -> program?.sessionFor(d)?.let { d to it } }

    val pastStart = weekStart.minusWeeks(1)

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
                    WorkoutWeekStrip(vm, weekStart, today)
                }
            }

            item {
                TodayCard(
                    program = program,
                    session = todaySession,
                    existing = todayLogForSession,
                    onStart = {
                        if (todaySession != null) vm.workouts.startOrOpenSession(today, todaySession)
                        else vm.workouts.startOutsidePlan(today)
                    },
                    onOpen = { log -> vm.workouts.openExisting(log) },
                    onLoadProgram = { vm.workouts.openProgram() },
                )
            }

            if (upcoming.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Дальше на этой неделе", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
                        upcoming.forEach { (date, session) ->
                            ScheduleRow(
                                whenText = Ru.weekdayDayMonth(date),
                                name = session.name,
                                trailing = kindLabel(session),
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Прошлая неделя", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
                    val pastLogs = logs.filter { it.date in pastStart..pastStart.plusDays(6) }
                        .sortedBy { it.date }
                    if (pastLogs.isEmpty()) {
                        Text(
                            "Нет отметок за ${Ru.weekRange(pastStart)}",
                            fontSize = 14.sp,
                            color = Palette.muted,
                        )
                    } else {
                        pastLogs.forEach { log ->
                            ScheduleRow(
                                whenText = Ru.weekdayDayMonth(log.date),
                                name = log.sessionName,
                                trailing = log.markLabel(),
                                onClick = { vm.workouts.openExisting(log) },
                            )
                        }
                    }
                }
            }

            item {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(shape)
                        .dashedBorder(Palette.dashed, 14.dp)
                        .clickable { vm.workouts.startOutsidePlan(today) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+ Тренировка вне плана", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    program: WorkoutProgram?,
    session: ProgramSession?,
    existing: WorkoutLog?,
    onStart: () -> Unit,
    onOpen: (WorkoutLog) -> Unit,
    onLoadProgram: () -> Unit,
) {
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
                Text("Сегодня · отдых", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
                Text("В программе нет занятия на этот день.", fontSize = 13.sp, color = Palette.muted)
                SecondaryButton("Тренировка вне плана", onClick = onStart)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Сегодня · ${session.name}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.ink,
                        modifier = Modifier.weight(1f),
                    )
                    MetaChip(if (existing != null) existing.markLabel() else "план")
                }
                Text(
                    buildString {
                        append("${session.exercises.size} ${Ru.plural(session.exercises.size, "упражнение", "упражнения", "упражнений")}")
                        existing?.let { append(" · ${it.markLabel()}") }
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.muted,
                )
                PrimaryButton(
                    if (existing != null) "Продолжить" else "Начать",
                    onClick = {
                        if (existing != null) onOpen(existing) else onStart()
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkoutWeekStrip(vm: AppViewModel, weekStart: LocalDate, today: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = weekStart.plusDays(i.toLong())
            val sel = d == today
            val mark = vm.workouts.weekMark(d)
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(shape)
                    .background(if (sel) Palette.accent else Palette.surface)
                    .border(
                        1.dp,
                        if (sel) Palette.accent else Palette.line,
                        shape,
                    )
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
                Spacer(Modifier.height(6.dp))
                Dot(
                    on = mark == WeekDayMark.DONE || mark == WeekDayMark.PARTIAL || (sel && mark != WeekDayMark.EMPTY),
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

private fun kindLabel(session: ProgramSession): String {
    val allCardio = session.exercises.isNotEmpty() && session.exercises.all { it.kind == ExerciseKind.CARDIO }
    return if (allCardio) "кардио" else "сила"
}
