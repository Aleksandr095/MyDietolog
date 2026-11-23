package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.DayMarks
import kotlin.math.roundToInt

@Composable
fun DayMarksCard(marks: DayMarks?, onChange: ((DayMarks) -> DayMarks) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Отметки дня", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Месячные",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.inkSoft,
                modifier = Modifier.weight(1f),
            )
            ChoiceChip("Да", selected = marks?.menstruation == true) {
                onChange { m -> m.copy(menstruation = if (m.menstruation == true) null else true) }
            }
            Spacer(Modifier.width(6.dp))
            ChoiceChip("Нет", selected = marks?.menstruation == false) {
                onChange { m -> m.copy(menstruation = if (m.menstruation == false) null else false) }
            }
        }

        ScaleRow("Голод", marks?.hunger) { value -> onChange { m -> m.copy(hunger = value) } }
        ScaleRow("Стресс", marks?.stress) { value -> onChange { m -> m.copy(stress = value) } }
    }
}

@Composable
private fun ScaleRow(label: String, value: Int?, onValue: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (value == null) "не отмечено" else "$value из ${DayMarks.SCALE_MAX}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.muted,
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
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
