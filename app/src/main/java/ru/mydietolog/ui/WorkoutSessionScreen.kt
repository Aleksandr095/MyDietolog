package ru.mydietolog.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.Ru

@Composable
fun WorkoutSessionScreen(vm: AppViewModel) {
    val draft = vm.workouts.draft ?: return
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .imePadding(),
    ) {
        ScreenHeader(
            title = draft.sessionName,
            subtitle = "${Ru.weekdayDayMonth(draft.date)} · факт можно править",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoBanner("Галочка у упражнения = факт выполнен. Подходы и минуты можно править позже.")
            }

            items(draft.exercises, key = { it.id }) { ex ->
                ExerciseCard(
                    ex = ex,
                    onToggle = { vm.workouts.toggleDone(ex) },
                    onAddSet = { vm.workouts.addSet(ex) },
                    onChanged = { vm.workouts.persistQuiet() },
                    onTechnique = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }

            item {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(shape)
                        .dashedBorder(Palette.dashed, 14.dp)
                        .clickable(role = Role.Button) { vm.workouts.openCustomExercise() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.add, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                    Text(
                        "  Своё упражнение",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.accent,
                    )
                }
            }
        }

        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                "Сохранить",
                onClick = { vm.workouts.finish() },
                icon = AppIcons.check,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                "Завершить",
                onClick = { vm.workouts.completeDraft() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    ex: ExerciseDraft,
    onToggle: () -> Unit,
    onAddSet: () -> Unit,
    onChanged: () -> Unit,
    onTechnique: (String) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DoneCheck(ex.done, onToggle)
            Text(
                ex.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (ex.techniqueUrl.isNullOrBlank()) Palette.ink else Palette.accent,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !ex.techniqueUrl.isNullOrBlank()) {
                        val url = ex.techniqueUrl ?: return@clickable
                        onTechnique(url)
                    },
            )
            if (ex.isCustom) {
                Text(
                    "вне плана",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.warnInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.warnBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Text(
            "в прошлый раз: ${ex.lastTimeLabel}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.muted,
        )

        if (ex.kind == ExerciseKind.CARDIO) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Минуты", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
                OutlinedTextField(
                    value = ex.minutes,
                    onValueChange = {
                        ex.minutes = it.filter { ch -> ch.isDigit() }.take(3)
                        onChanged()
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(10.dp),
                    colors = appFieldColors(Palette.bg),
                    modifier = Modifier.width(96.dp),
                )
            }
        } else {
            SetTable(ex, onAddSet, onChanged)
        }
    }
}

@Composable
private fun SetTable(ex: ExerciseDraft, onAddSet: () -> Unit, onChanged: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("#", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.muted, modifier = Modifier.width(28.dp))
            Text("Вес", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.muted, modifier = Modifier.weight(1f))
            Text("Повторы", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.muted, modifier = Modifier.weight(1f))
            Box(Modifier.width(28.dp))
        }
        ex.sets.forEachIndexed { index, set ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.ink, modifier = Modifier.width(28.dp))
                OutlinedTextField(
                    value = set.weight,
                    onValueChange = {
                        set.weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6)
                        onChanged()
                    },
                    singleLine = true,
                    placeholder = { Text("—") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(10.dp),
                    colors = appFieldColors(Palette.bg),
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                OutlinedTextField(
                    value = set.reps,
                    onValueChange = {
                        set.reps = it.filter { ch -> ch.isDigit() }.take(4)
                        onChanged()
                    },
                    singleLine = true,
                    placeholder = { Text("—") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(10.dp),
                    colors = appFieldColors(Palette.bg),
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                DoneCheck(set.done) {
                    set.done = !set.done
                    onChanged()
                }
            }
        }
        Text(
            "+ подход",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.accent,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onAddSet)
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
fun DoneCheck(done: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (done) Palette.accent else Palette.surface)
            .border(1.5.dp, if (done) Palette.accent else Palette.field, RoundedCornerShape(7.dp))
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(AppIcons.check, contentDescription = "Выполнено", tint = ColorWhite, modifier = Modifier.size(16.dp))
        }
    }
}

private val ColorWhite = androidx.compose.ui.graphics.Color.White
