package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.mydietolog.data.DayMarks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

private enum class MarkScale { HUNGER, STRESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMarksCard(
    date: LocalDate,
    marks: DayMarks?,
    onChange: ((DayMarks) -> DayMarks) -> Unit,
    onMenstruation: (LocalDate, LocalDate, Boolean?) -> Unit,
) {
    var scale by remember { mutableStateOf<MarkScale?>(null) }
    var periodOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Отметки дня",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.muted,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MarkCircle(
                title = "Месячные",
                value = when (marks?.menstruation) {
                    true -> "да"
                    false -> "нет"
                    null -> "+"
                },
                filled = marks?.menstruation != null,
                caption = when (marks?.menstruation) {
                    true -> "да"
                    false -> "нет"
                    null -> "Добавить"
                },
                onClick = {
                    scale = null
                    periodOpen = true
                },
            )
            MarkCircle(
                title = "Голод",
                value = marks?.hunger?.toString() ?: "+",
                filled = marks?.hunger != null,
                caption = marks?.hunger?.let { "$it из ${DayMarks.SCALE_MAX}" } ?: "Добавить",
                onClick = { scale = if (scale == MarkScale.HUNGER) null else MarkScale.HUNGER },
            )
            MarkCircle(
                title = "Стресс",
                value = marks?.stress?.toString() ?: "+",
                filled = marks?.stress != null,
                caption = marks?.stress?.let { "$it из ${DayMarks.SCALE_MAX}" } ?: "Добавить",
                onClick = { scale = if (scale == MarkScale.STRESS) null else MarkScale.STRESS },
            )
        }
        if (scale != null) {
            val value = if (scale == MarkScale.HUNGER) marks?.hunger else marks?.stress
            ScaleEditor(
                label = if (scale == MarkScale.HUNGER) "Голод" else "Стресс",
                value = value,
                onValue = { next ->
                    onChange { m ->
                        if (scale == MarkScale.HUNGER) m.copy(hunger = next) else m.copy(stress = next)
                    }
                },
            )
        }
    }
    if (periodOpen) {
        MenstruationPeriodDialog(
            monthMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            onDismiss = { periodOpen = false },
            onSave = { from, to ->
                periodOpen = false
                onMenstruation(from, to, true)
            },
            onClear = { from, to ->
                periodOpen = false
                onMenstruation(from, to, null)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenstruationPeriodDialog(
    monthMillis: Long,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate) -> Unit,
    onClear: (LocalDate, LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState(initialDisplayedMonthMillis = monthMillis)
    val startMillis = state.selectedStartDateMillis
    val endMillis = state.selectedEndDateMillis
    val ready = startMillis != null
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Palette.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    showModeToggle = false,
                    title = {
                        Text(
                            "Период сохранится на каждый день от и до",
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.inkSoft,
                        )
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            val start = startMillis ?: return@TextButton
                            val range = menstruationRange(start, endMillis)
                            onClear(range.first, range.second)
                        },
                        enabled = ready,
                    ) {
                        Text("Снять", color = if (ready) Palette.accent else Palette.muted)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = Palette.accent)
                    }
                    TextButton(
                        onClick = {
                            val start = startMillis ?: return@TextButton
                            val range = menstruationRange(start, endMillis)
                            onSave(range.first, range.second)
                        },
                        enabled = ready,
                    ) {
                        Text("Сохранить", color = if (ready) Palette.accent else Palette.muted)
                    }
                }
            }
        }
    }
}

private fun menstruationRange(startMillis: Long, endMillis: Long?): Pair<LocalDate, LocalDate> {
    val start = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val end = if (endMillis == null) {
        start
    } else {
        Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
    }
    return if (start.isAfter(end)) end to start else start to end
}

@Composable
private fun MarkCircle(
    title: String,
    value: String,
    filled: Boolean,
    caption: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .then(if (filled) Modifier.border(1.5.dp, Palette.accent, CircleShape) else Modifier)
                .background(if (filled) Palette.surface else Palette.placeholder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                fontSize = if (value == "+") 28.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) Palette.accent else Palette.ink,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(caption, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
    }
}

@Composable
private fun ScaleEditor(label: String, value: Int?, onValue: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.inkSoft,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                TextButton(onClick = { onValue(null) }) {
                    Text("Сбросить", fontSize = 12.sp, color = Palette.muted)
                }
            }
        }
        Slider(
            value = (value ?: 0).toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = 0f..DayMarks.SCALE_MAX.toFloat(),
            steps = DayMarks.SCALE_MAX - 1,
            colors = SliderDefaults.colors(
                thumbColor = Palette.accent,
                activeTrackColor = Palette.accent,
                inactiveTrackColor = Palette.field,
            ),
        )
    }
}

@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Palette.accent else Palette.surface)
            .border(1.dp, if (selected) Palette.accent else Palette.field, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Palette.ink,
        )
    }
}
