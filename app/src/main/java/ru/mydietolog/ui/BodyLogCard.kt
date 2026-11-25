package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.BodyLog
import ru.mydietolog.data.Ru
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class BodyDateTarget { WEIGHT, MEASURES, SEMAVIK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyLogCard(
    selectedDate: LocalDate,
    entries: List<BodyLog>,
    onSaveWeight: (LocalDate, Double?) -> Unit,
    onSaveMeasurements: (LocalDate, Double?, Double?, Double?, Double?, Double?) -> Unit,
    onSaveSemavik: (LocalDate, Double?) -> Unit,
) {
    val weight = remember {
        WeightFields(selectedDate, entries.firstOrNull { it.date == selectedDate }?.weightKg)
    }
    val measures = remember {
        MeasureFields(selectedDate, entries.firstOrNull { it.date == selectedDate })
    }
    val semavik = remember {
        SemavikFields(selectedDate, entries.firstOrNull { it.date == selectedDate }?.semavikMg)
    }
    var picking by remember { mutableStateOf<BodyDateTarget?>(null) }

    fun saved(date: LocalDate) = entries.firstOrNull { it.date == date }

    LaunchedEffect(selectedDate) {
        picking = null
        weight.date = selectedDate
        weight.kilograms.show(saved(selectedDate)?.weightKg, force = true)
        measures.date = selectedDate
        measures.show(saved(selectedDate), force = true)
        semavik.date = selectedDate
        semavik.dose.show(saved(selectedDate)?.semavikMg, force = true)
    }
    LaunchedEffect(entries) {
        weight.kilograms.show(saved(weight.date)?.weightKg, force = false)
        measures.show(saved(measures.date), force = false)
        semavik.dose.show(saved(semavik.date)?.semavikMg, force = false)
    }

    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Вес, замеры, Семавик",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Вес")
            DateField("Дата", weight.date) { picking = BodyDateTarget.WEIGHT }
            DecimalField("Килограммы", weight.kilograms.text) { weight.kilograms.text = it }
            SaveButton(enabled = inputsValid(weight.kilograms.text)) {
                when (val parsed = parseBodyNumber(weight.kilograms.text)) {
                    ParsedNumber.Invalid -> Unit
                    ParsedNumber.Empty -> {
                        weight.kilograms.commit("")
                        onSaveWeight(weight.date, null)
                    }
                    is ParsedNumber.Ok -> {
                        weight.kilograms.commit(formatBodyNumber(parsed.number))
                        onSaveWeight(weight.date, parsed.number)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Замеры")
            Text(
                "В сантиметрах",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.muted,
            )
            DateField("Дата", measures.date) { picking = BodyDateTarget.MEASURES }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("Талия", measures.waist.text, Modifier.weight(1f)) { measures.waist.text = it }
                DecimalField("Бёдра", measures.hips.text, Modifier.weight(1f)) { measures.hips.text = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("Грудь", measures.chest.text, Modifier.weight(1f)) { measures.chest.text = it }
                DecimalField("Бедро", measures.thigh.text, Modifier.weight(1f)) { measures.thigh.text = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("Рука", measures.arm.text, Modifier.weight(1f)) { measures.arm.text = it }
                Spacer(Modifier.weight(1f))
            }
            val measuresValid = inputsValid(
                measures.waist.text,
                measures.hips.text,
                measures.chest.text,
                measures.thigh.text,
                measures.arm.text,
            )
            SaveButton(enabled = measuresValid) {
                val fields = listOf(measures.waist, measures.hips, measures.chest, measures.thigh, measures.arm)
                val parsed = fields.map { parseBodyNumber(it.text) }
                if (parsed.any { it is ParsedNumber.Invalid }) return@SaveButton
                val numbers = parsed.map { (it as? ParsedNumber.Ok)?.number }
                fields.forEachIndexed { index, field -> field.commit(formatBodyNumber(numbers[index])) }
                onSaveMeasurements(
                    measures.date,
                    numbers[0],
                    numbers[1],
                    numbers[2],
                    numbers[3],
                    numbers[4],
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Семавик")
            DateField("Дата приёма", semavik.date) { picking = BodyDateTarget.SEMAVIK }
            DecimalField("Дозировка, мг", semavik.dose.text) { semavik.dose.text = it }
            SaveButton(enabled = inputsValid(semavik.dose.text)) {
                when (val parsed = parseBodyNumber(semavik.dose.text)) {
                    ParsedNumber.Invalid -> Unit
                    ParsedNumber.Empty -> {
                        semavik.dose.commit("")
                        onSaveSemavik(semavik.date, null)
                    }
                    is ParsedNumber.Ok -> {
                        semavik.dose.commit(formatBodyNumber(parsed.number))
                        onSaveSemavik(semavik.date, parsed.number)
                    }
                }
            }
        }
    }

    val target = picking
    if (target != null) {
        val initial = when (target) {
            BodyDateTarget.WEIGHT -> weight.date
            BodyDateTarget.MEASURES -> measures.date
            BodyDateTarget.SEMAVIK -> semavik.date
        }
        BodyDateDialog(
            initial = initial,
            onDismiss = { picking = null },
            onPick = { picked ->
                when (target) {
                    BodyDateTarget.WEIGHT -> {
                        weight.date = picked
                        weight.kilograms.show(saved(picked)?.weightKg, force = true)
                    }
                    BodyDateTarget.MEASURES -> {
                        measures.date = picked
                        measures.show(saved(picked), force = true)
                    }
                    BodyDateTarget.SEMAVIK -> {
                        semavik.date = picked
                        semavik.dose.show(saved(picked)?.semavikMg, force = true)
                    }
                }
                picking = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(
                "Сохранить",
                color = if (enabled) Palette.accent else Palette.muted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DateField(label: String, date: LocalDate, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Palette.field, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${Ru.dayMonth(date)} ${date.year}",
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.ink,
            )
            Icon(
                AppIcons.calendar,
                contentDescription = "Выбрать дату",
                tint = Palette.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted, maxLines = 1)
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                onValue(raw.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6))
            },
            placeholder = { Text("—") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(10.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyDateDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val selected = state.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = selected != null,
            ) {
                Text("Выбрать", color = if (selected != null) Palette.accent else Palette.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Palette.accent) }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private class NumberField(initial: Double?) {
    var text by mutableStateOf(formatBodyNumber(initial))
    private var baseline = text

    fun show(value: Double?, force: Boolean) {
        val formatted = formatBodyNumber(value)
        if (!force && text != baseline) return
        text = formatted
        baseline = formatted
    }

    fun commit(formatted: String) {
        text = formatted
        baseline = formatted
    }
}

private class WeightFields(date: LocalDate, kilograms: Double?) {
    var date by mutableStateOf(date)
    val kilograms = NumberField(kilograms)
}

private class MeasureFields(date: LocalDate, entry: BodyLog?) {
    var date by mutableStateOf(date)
    val waist = NumberField(entry?.waistCm)
    val hips = NumberField(entry?.hipsCm)
    val chest = NumberField(entry?.chestCm)
    val thigh = NumberField(entry?.thighCm)
    val arm = NumberField(entry?.armCm)

    fun show(entry: BodyLog?, force: Boolean) {
        waist.show(entry?.waistCm, force)
        hips.show(entry?.hipsCm, force)
        chest.show(entry?.chestCm, force)
        thigh.show(entry?.thighCm, force)
        arm.show(entry?.armCm, force)
    }
}

private class SemavikFields(date: LocalDate, dose: Double?) {
    var date by mutableStateOf(date)
    val dose = NumberField(dose)
}

private sealed interface ParsedNumber {
    data object Empty : ParsedNumber
    data class Ok(val number: Double) : ParsedNumber
    data object Invalid : ParsedNumber
}

private fun parseBodyNumber(raw: String): ParsedNumber {
    val text = raw.trim().replace(',', '.')
    if (text.isEmpty()) return ParsedNumber.Empty
    val number = text.toDoubleOrNull()
    if (number == null || number.isNaN() || number.isInfinite() || number < 0.0) return ParsedNumber.Invalid
    return ParsedNumber.Ok(number)
}

private fun inputsValid(vararg raw: String): Boolean =
    raw.all { parseBodyNumber(it) !is ParsedNumber.Invalid }

private fun formatBodyNumber(value: Double?): String {
    if (value == null) return ""
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')
}
