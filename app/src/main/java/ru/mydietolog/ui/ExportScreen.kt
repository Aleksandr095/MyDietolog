package ru.mydietolog.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ru.mydietolog.data.DaySummary
import ru.mydietolog.data.Ru
import ru.mydietolog.data.WeekSummary
import java.io.File
import java.time.LocalDate

@Composable
fun ExportScreen(vm: AppViewModel, initialStart: LocalDate) {
    val all by vm.meals.collectAsState()
    val workouts by vm.workouts.logs.collectAsState()
    var start by remember { mutableStateOf(initialStart) }
    val context = LocalContext.current
    val today = vm.today()
    val week = WeekSummary.build(start, all, workouts)
    val incomplete = week.incompleteDays(today)
    val canExport = week.mealCount > 0 || week.workoutCount > 0

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(title = "Выгрузка недели", onBack = { vm.back() })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val boxShape = RoundedCornerShape(18.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(boxShape)
                    .background(Palette.surface)
                    .border(1.dp, Palette.line, boxShape)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(AppIcons.left, "Предыдущая неделя", { start = start.minusWeeks(1) })
                Text(
                    Ru.weekRange(start),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.ink,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundIcon(AppIcons.right, "Следующая неделя", { start = start.plusWeeks(1) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("7", "дней", Modifier.weight(1f))
                Stat(week.mealCount.toString(), Ru.plural(week.mealCount, "приём", "приёма", "приёмов"), Modifier.weight(1f))
                Stat(week.workoutCount.toString(), Ru.plural(week.workoutCount, "тренировка", "тренировки", "тренировок"), Modifier.weight(1f))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(boxShape)
                    .background(Palette.surface)
                    .border(1.dp, Palette.line, boxShape)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                week.days.forEachIndexed { i, day ->
                    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Ru.shortDay(day.date), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.ink, modifier = Modifier.weight(1f))
                        Text(
                            dayLineSummary(day),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.muted,
                        )
                    }
                    if (i < week.days.lastIndex) HorizontalDivider(color = Palette.line)
                }
            }

            if (incomplete.isNotEmpty()) {
                val text = incomplete.joinToString("; ") { d ->
                    "${Ru.dayMonth(d.date)} — нет ${d.missingMain.joinToString(", ") { it.genitive }}"
                }
                InfoBanner("Неполные дни: $text. В архиве они будут помечены.", warning = true)
            }

            Row(
                Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.download, contentDescription = null, tint = Palette.muted, modifier = Modifier.size(20.dp))
                Text(
                    "${vm.exportFileName(week)}\ndiary.md · diary.json · photos/\nпитание + факты тренировок",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.muted,
                )
            }
        }

        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                if (vm.busy) "Собираю архив…" else "Создать архив и поделиться",
                onClick = { vm.export(week) { zip -> share(context, zip) } },
                icon = AppIcons.share,
                enabled = !vm.busy && canExport,
            )
            SecondaryButton(
                "Только сохранить в «Загрузки»",
                onClick = {
                    vm.exportToDownloads(week) { ok ->
                        Toast.makeText(
                            context,
                            if (ok) "Сохранено: Загрузки/MyDietolog" else "Не удалось сохранить архив",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                enabled = !vm.busy && canExport,
            )
        }
    }
}

private fun dayLineSummary(day: DaySummary): String {
    if (!day.hasContent) return "нет записей"
    val parts = mutableListOf<String>()
    if (day.meals.isNotEmpty()) {
        parts += Ru.meals(day.meals.size)
        if (day.photoCount > 0) parts += Ru.photos(day.photoCount)
    }
    if (day.workouts.isNotEmpty()) {
        parts += "${day.workouts.size} ${Ru.plural(day.workouts.size, "трен.", "трен.", "трен.")}"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(12.dp),
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
    }
}

private fun share(context: Context, zip: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, zip.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Отправить архив"))
}
