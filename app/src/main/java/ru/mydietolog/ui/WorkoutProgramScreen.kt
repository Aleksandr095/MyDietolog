package ru.mydietolog.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.Ru
import ru.mydietolog.data.WorkoutProgram
import java.time.Instant
import java.time.ZoneId

@Composable
fun WorkoutProgramScreen(vm: AppViewModel) {
    val program by vm.workouts.program.collectAsState()
    val preview = vm.workouts.programPreview
    val error = vm.workouts.programError
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        vm.workouts.pickProgramFile(uri, name)
    }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(
            title = "Программа",
            subtitle = "Файл JSON · факты сохраняются при замене",
            onBack = { vm.back() },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProgramCard(
                title = "Текущая",
                badge = if (program != null) "активна" else "нет",
                program = program,
                emptyText = "Программа не загружена. Выберите JSON-файл.",
            )

            SecondaryButton("Изменить план", onClick = { vm.workouts.openProgramEditor() })

            SecondaryButton(
                "Выбрать файл программы…",
                onClick = {
                    picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "text/*", "*/*"))
                },
            )

            if (error != null) {
                InfoBanner(error, warning = true)
            }

            if (preview != null) {
                ProgramCard(
                    title = "Новый файл",
                    badge = "превью",
                    program = preview,
                    emptyText = "",
                )
                PrimaryButton("Заменить программу", onClick = { vm.workouts.confirmReplaceProgram() })
                Text(
                    "Отмеченные тренировки и история «в прошлый раз» останутся. Меняется только план на будущее.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.muted,
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(
    title: String,
    badge: String,
    program: WorkoutProgram?,
    emptyText: String,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink, modifier = Modifier.weight(1f))
            MetaChip(badge)
        }
        if (program == null) {
            Text(emptyText, fontSize = 13.sp, color = Palette.muted)
            return
        }
        Text(program.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
        Text(
            buildString {
                program.fileName?.let { append("Файл: $it · ") }
                append("${program.sessions.size} ${Ru.plural(program.sessions.size, "тип", "типа", "типов")} занятий")
                append(" · загружена ${formatLoaded(program.loadedAt)}")
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.muted,
        )
        program.sessions.sortedBy { it.weekday }.forEach { s ->
            val day = Ru.weekdaysShort.getOrElse(s.weekday - 1) { "?" }
            Text(
                "$day — ${s.name} · ${s.exercises.size} упр.",
                fontSize = 13.sp,
                color = Palette.inkSoft,
            )
        }
    }
}

private fun formatLoaded(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return Ru.dayMonth(d)
}
