package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ProgramExercise
import ru.mydietolog.data.Ru
import java.time.LocalDate

/** План занятия по программе без записи факта. */
@Composable
fun WorkoutPlanScreen(vm: AppViewModel, date: LocalDate) {
    val program by vm.workouts.program.collectAsState()
    val session = program?.sessionFor(date)
    val today = vm.today()

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(
            title = session?.name ?: "Нет занятия",
            subtitle = "${Ru.weekdayDayMonth(date)} · по программе",
            onBack = { vm.back() },
        )

        if (session == null) {
            Column(Modifier.padding(16.dp)) {
                InfoBanner("В этот день в программе нет тренировки.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(session.exercises, key = { _, ex -> ex.id }) { index, ex ->
                    PlanExerciseRow(index + 1, ex)
                }
            }

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    date.isAfter(today) ->
                        InfoBanner("Тренировку можно начать, когда наступит этот день.")
                    else -> {
                        PrimaryButton("Начать тренировку", onClick = { vm.workouts.startOrOpenSession(date, session) })
                        Text(
                            "Откроется экран факта — подходы, вес и галочки.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanExerciseRow(index: Int, ex: ProgramExercise) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$index.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
            Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink, modifier = Modifier.weight(1f))
        }
        if (ex.target.isNotBlank()) {
            Text("Цель: ${ex.target}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        }
        if (ex.note.isNotBlank()) {
            Text(ex.note, fontSize = 13.sp, color = Palette.inkSoft)
        }
        if (ex.kind == ExerciseKind.CARDIO) {
            Text("Кардио · минуты", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        }
    }
}