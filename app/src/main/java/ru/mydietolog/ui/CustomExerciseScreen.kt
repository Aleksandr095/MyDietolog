package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomExerciseScreen(vm: AppViewModel) {
    val w = vm.workouts
    val names by w.customNames.collectAsState()
    val suggestions = w.nameSuggestions().ifEmpty {
        if (w.customName.isBlank()) names.take(5) else emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .imePadding(),
    ) {
        ScreenHeader(
            title = "Своё упражнение",
            subtitle = "Сохранится в факте с пометкой «вне плана»",
            onBack = { vm.back() },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldLabel("Название")
            OutlinedTextField(
                value = w.customName,
                onValueChange = { w.customName = it },
                placeholder = { Text("Например, выпады") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(12.dp),
                colors = appFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (suggestions.isNotEmpty()) {
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Palette.surface)
                        .border(1.dp, Palette.line, shape),
                ) {
                    suggestions.forEachIndexed { i, s ->
                        Text(
                            s,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { w.customName = s }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                        if (i < suggestions.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(color = Palette.line)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberBox("Подходы", w.customSets, { w.customSets = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
                NumberBox("Повторы", w.customReps, { w.customReps = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f))
                NumberBox("Вес, кг", w.customWeight, {
                    w.customWeight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6)
                }, Modifier.weight(1f))
            }

            Text(
                "вне плана",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.warnInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.warnBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
            PrimaryButton(
                "Добавить в факт",
                onClick = { w.addCustomExercise() },
                icon = AppIcons.check,
                enabled = w.customName.isNotBlank() && w.draft != null,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
}

@Composable
private fun NumberBox(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text("—") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

