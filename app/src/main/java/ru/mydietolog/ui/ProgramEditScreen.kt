package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.ExerciseKind
import ru.mydietolog.data.ProgramExercise
import ru.mydietolog.data.ProgramSession
import ru.mydietolog.data.Ru
import ru.mydietolog.data.WorkoutProgram
import java.util.UUID

class ExerciseEdit(
    val id: String = UUID.randomUUID().toString(),
    name: String = "",
    sets: String = "3",
    reps: String = "10",
    weight: String = "",
    minutes: String = "",
    note: String = "",
    val techniqueUrl: String? = null,
    kind: ExerciseKind = ExerciseKind.STRENGTH,
) {
    var name by mutableStateOf(name)
    var sets by mutableStateOf(sets)
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)
    var minutes by mutableStateOf(minutes)
    var note by mutableStateOf(note)
    var kind by mutableStateOf(kind)

    fun toExercise(): ProgramExercise? {
        val title = name.trim()
        if (title.isBlank()) return null
        val cardio = kind == ExerciseKind.CARDIO
        return ProgramExercise(
            id = id,
            name = title,
            goalSets = if (cardio) null else sets.toIntOrNull(),
            goalReps = if (cardio) "" else reps.trim(),
            goalWeight = if (cardio) "" else weight.trim(),
            goalMinutes = if (cardio) minutes.toIntOrNull() else null,
            note = note.trim(),
            techniqueUrl = techniqueUrl,
            kind = kind,
        )
    }

    companion object {
        fun from(ex: ProgramExercise) = ExerciseEdit(
            id = ex.id,
            name = ex.name,
            sets = ex.goalSets?.toString() ?: "",
            reps = ex.goalReps,
            weight = ex.goalWeight,
            minutes = ex.goalMinutes?.toString() ?: "",
            note = ex.note,
            techniqueUrl = ex.techniqueUrl,
            kind = ex.kind,
        )
    }
}

class SessionEdit(
    val id: String = UUID.randomUUID().toString(),
    name: String = "Новое занятие",
    weekday: Int = 1,
    exercises: List<ExerciseEdit> = listOf(ExerciseEdit()),
) {
    var name by mutableStateOf(name)
    var weekday by mutableStateOf(weekday)
    val exercises: SnapshotStateList<ExerciseEdit> = mutableStateListOf<ExerciseEdit>().apply { addAll(exercises) }

    fun toSession(): ProgramSession = ProgramSession(
        id = id,
        name = name.trim().ifBlank { "Занятие" },
        weekday = weekday.coerceIn(1, 7),
        exercises = exercises.mapNotNull { it.toExercise() },
    )

    companion object {
        fun from(session: ProgramSession) = SessionEdit(
            id = session.id,
            name = session.name,
            weekday = session.weekday,
            exercises = session.exercises.map { ExerciseEdit.from(it) }.ifEmpty { listOf(ExerciseEdit()) },
        )
    }
}

class ProgramEdit(
    val id: String,
    val title: String,
    val fileName: String?,
    val sessions: SnapshotStateList<SessionEdit>,
) {
    fun toProgram() = WorkoutProgram(
        id = id,
        title = title.ifBlank { "Программа" },
        fileName = fileName,
        sessions = sessions.map { it.toSession() },
    )

    companion object {
        fun from(program: WorkoutProgram) = ProgramEdit(
            id = program.id,
            title = program.title,
            fileName = program.fileName,
            sessions = mutableStateListOf<SessionEdit>().apply { addAll(program.sessions.map { SessionEdit.from(it) }) },
        )

        fun blank() = ProgramEdit(
            id = UUID.randomUUID().toString(),
            title = "Моя программа",
            fileName = null,
            sessions = mutableStateListOf(SessionEdit()),
        )
    }
}

@Composable
fun ProgramEditScreen(vm: AppViewModel) {
    val edit = vm.workouts.programEdit
    var confirmDeleteProgram by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .imePadding(),
    ) {
        ScreenHeader(
            title = "План",
            subtitle = edit?.title ?: "Программа",
            onBack = { vm.back() },
        )
        if (edit == null) {
            Column(Modifier.padding(16.dp)) {
                InfoBanner("План не открыт.")
            }
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Правки записываются в программу на телефоне. Файл, из которого она загружалась, не меняется.",
                fontSize = 13.sp,
                color = Palette.muted,
            )
            edit.sessions.forEach { session ->
                SessionEditor(session, onWeekday = { vm.workouts.setSessionWeekday(session, it) }) {
                    edit.sessions.remove(session)
                }
            }
            SecondaryButton("Добавить занятие", onClick = { edit.sessions.add(SessionEdit()) })
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton("Сохранить план", onClick = { vm.workouts.saveProgramEditor() }, icon = AppIcons.check)
            TextButton(onClick = { confirmDeleteProgram = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Удалить программу", fontWeight = FontWeight.Bold, color = Palette.danger)
            }
        }
        if (confirmDeleteProgram) {
            AlertDialog(
                onDismissRequest = { confirmDeleteProgram = false },
                title = { Text("Удалить программу?") },
                text = { Text("Занятия пропадут из календаря. Записанные тренировки останутся в истории.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteProgram = false
                        vm.workouts.deleteProgram()
                    }) { Text("Удалить", color = Palette.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteProgram = false }) { Text("Отмена") }
                },
            )
        }
    }
}

@Composable
private fun SessionEditor(session: SessionEdit, onWeekday: (Int) -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Занятие",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) {
                Text("Удалить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.danger)
            }
        }
        OutlinedTextField(
            value = session.name,
            onValueChange = { session.name = it },
            label = { Text("Название") },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(Palette.bg),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("День недели", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Ru.weekdaysShort.forEachIndexed { index, label ->
                ChoiceChip(label, selected = session.weekday == index + 1) { onWeekday(index + 1) }
            }
        }
        Text("Упражнения", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        session.exercises.forEach { exercise ->
            ExerciseEditor(exercise) { session.exercises.remove(exercise) }
        }
        SecondaryButton("Добавить упражнение", onClick = { session.exercises.add(ExerciseEdit()) })
    }
}

@Composable
private fun ExerciseEditor(exercise: ExerciseEdit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = "Удалить упражнение" },
            ) {
                Icon(AppIcons.delete, contentDescription = null, tint = Palette.muted)
            }
        }
        OutlinedTextField(
            value = exercise.name,
            onValueChange = { exercise.name = it.take(100) },
            label = { Text("Упражнение") },
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp),
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(Palette.surface),
            modifier = Modifier.fillMaxWidth(),
        )
        if (exercise.kind == ExerciseKind.CARDIO) {
            GoalInput("Минуты", exercise.minutes, KeyboardType.Number) {
                exercise.minutes = it.filter(Char::isDigit).take(3)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalInput("Подходы", exercise.sets, KeyboardType.Number, Modifier.weight(1f)) {
                    exercise.sets = it.filter(Char::isDigit).take(2)
                }
                GoalInput("Повторы", exercise.reps, KeyboardType.Text, Modifier.weight(1f)) {
                    exercise.reps = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '–' || ch.isLetter() || ch == ' ' }.take(12)
                }
                GoalInput("Вес, кг", exercise.weight, KeyboardType.Decimal, Modifier.weight(1f)) {
                    exercise.weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == '-' || ch == '–' }.take(8)
                }
            }
        }
        OutlinedTextField(
            value = exercise.note,
            onValueChange = { exercise.note = it.take(500) },
            label = { Text("Заметка") },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(Palette.surface),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Сила", selected = exercise.kind == ExerciseKind.STRENGTH) { exercise.kind = ExerciseKind.STRENGTH }
            ChoiceChip("Кардио", selected = exercise.kind == ExerciseKind.CARDIO) { exercise.kind = ExerciseKind.CARDIO }
        }
    }
}

@Composable
private fun GoalInput(
    label: String,
    value: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = TextStyle(fontSize = 15.sp),
        shape = RoundedCornerShape(12.dp),
        colors = appFieldColors(Palette.surface),
        modifier = modifier.fillMaxWidth(),
    )
}
