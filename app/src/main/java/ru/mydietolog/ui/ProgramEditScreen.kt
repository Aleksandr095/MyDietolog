package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    target: String = "3 × 10",
    note: String = "",
    val techniqueUrl: String? = null,
    kind: ExerciseKind = ExerciseKind.STRENGTH,
) {
    var name by mutableStateOf(name)
    var target by mutableStateOf(target)
    var note by mutableStateOf(note)
    var kind by mutableStateOf(kind)

    fun toExercise(): ProgramExercise? {
        val title = name.trim()
        if (title.isBlank()) return null
        return ProgramExercise(
            id = id,
            name = title,
            target = target.trim(),
            note = note.trim(),
            techniqueUrl = techniqueUrl,
            kind = kind,
        )
    }

    companion object {
        fun from(ex: ProgramExercise) = ExerciseEdit(ex.id, ex.name, ex.target, ex.note, ex.techniqueUrl, ex.kind)
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
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
            PrimaryButton("Сохранить план", onClick = { vm.workouts.saveProgramEditor() }, icon = AppIcons.check)
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = session.name,
            onValueChange = { session.name = it },
            label = { Text("Занятие") },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(Palette.bg),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("День недели", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ru.weekdaysShort.forEachIndexed { index, label ->
                ChoiceChip(label, selected = session.weekday == index + 1) { onWeekday(index + 1) }
            }
        }
        session.exercises.forEach { exercise ->
            ExerciseEditor(exercise) { session.exercises.remove(exercise) }
        }
        SecondaryButton("Добавить упражнение", onClick = { session.exercises.add(ExerciseEdit()) })
        SecondaryButton("Удалить занятие", onClick = onDelete)
    }
}

@Composable
private fun ExerciseEditor(exercise: ExerciseEdit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = exercise.name,
                onValueChange = { exercise.name = it },
                label = { Text("Упражнение") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp),
                shape = RoundedCornerShape(12.dp),
                colors = appFieldColors(Palette.bg),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Удалить упражнение" }) {
                Icon(AppIcons.delete, contentDescription = null, tint = Palette.muted)
            }
        }
        OutlinedTextField(
            value = exercise.target,
            onValueChange = { exercise.target = it },
            label = { Text("Цель") },
            placeholder = { Text("3 × 10–12") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(Palette.bg),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = exercise.note,
            onValueChange = { exercise.note = it },
            label = { Text("Заметка") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(Palette.bg),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Сила", selected = exercise.kind == ExerciseKind.STRENGTH) { exercise.kind = ExerciseKind.STRENGTH }
            ChoiceChip("Кардио", selected = exercise.kind == ExerciseKind.CARDIO) { exercise.kind = ExerciseKind.CARDIO }
        }
    }
}
