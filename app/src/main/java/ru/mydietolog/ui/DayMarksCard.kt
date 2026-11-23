package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.mydietolog.data.DayMarks
import kotlin.math.roundToInt

private enum class MarkScale { HUNGER, STRESS }

@Composable
fun DayMarksCard(marks: DayMarks?, onChange: ((DayMarks) -> DayMarks) -> Unit) {
    var scale by remember { mutableStateOf<MarkScale?>(null) }
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
                    onChange { m ->
                        m.copy(
                            menstruation = when (m.menstruation) {
                                null -> true
                                true -> false
                                false -> null
                            },
                        )
                    }
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
                .clip(CircleShape)
                .background(if (filled) Palette.accent else Palette.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                fontSize = if (value == "+") 28.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) Color.White else Palette.ink,
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
