package ru.mydietolog.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mydietolog.data.MAX_PHOTOS_PER_DISH
import ru.mydietolog.data.MealType
import ru.mydietolog.data.Ru

@Composable
fun MealEditScreen(vm: AppViewModel) {
    val draft = vm.draft ?: return
    val context = LocalContext.current

    var confirmDelete by remember { mutableStateOf(false) }
    var sourceFor by remember { mutableStateOf<DishDraft?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val dish = draft.dishes.firstOrNull { it.id == vm.pendingDishId }
        val uri = vm.pendingCameraUri
        if (ok && dish != null && uri != null) vm.addPhoto(dish, uri)
        vm.pendingCameraUri = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val dish = draft.dishes.firstOrNull { it.id == vm.pendingDishId }
        if (uri != null && dish != null) vm.addPhoto(dish, uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .imePadding(),
    ) {
        ScreenHeader(
            title = "Приём пищи",
            subtitle = Ru.weekdayDayMonth(draft.date),
            onBack = { vm.saveDraft() },
        ) {
            if (!draft.isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(AppIcons.delete, contentDescription = "Удалить запись", tint = Palette.muted)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TypeSelector(draft.type) { draft.type = it } }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label("Время · необязательно")
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(shape)
                                .background(Palette.surface)
                                .border(1.dp, Palette.field, shape)
                                .clickable(role = Role.Button) {
                                    val now = java.time.LocalTime.now()
                                    val parts = draft.time?.split(":")
                                    val h = parts?.getOrNull(0)?.toIntOrNull() ?: now.hour
                                    val m = parts?.getOrNull(1)?.toIntOrNull() ?: now.minute
                                    TimePickerDialog(context, { _, hh, mm ->
                                        draft.time = "%02d:%02d".format(hh, mm)
                                    }, h, m, true).show()
                                }
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                draft.time ?: "—",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (draft.time == null) Palette.muted else Palette.ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (draft.time != null) {
                                IconButton(onClick = { draft.time = null }) {
                                    Icon(AppIcons.close, contentDescription = "Очистить время", tint = Palette.muted, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Icon(AppIcons.clock, contentDescription = null, tint = Palette.muted, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label("Моя оценка, итого")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Palette.accentSoft)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                draft.totalKcal?.let { "$it ккал" } ?: "—",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Palette.accentDark,
                                modifier = Modifier.weight(1f),
                            )
                            Text("сумма блюд", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.accentDark)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Блюда · ${draft.dishes.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
                    InfoBanner("Фото 1 — сверху, фото 2 — сбоку или этикетка. В описании укажите масло, соус, сахар — сколько.")
                }
            }

            itemsIndexed(draft.dishes, key = { _, d -> d.id }) { index, dish ->
                DishCard(
                    vm = vm,
                    dish = dish,
                    index = index,
                    onAddPhoto = {
                        vm.pendingDishId = dish.id
                        sourceFor = dish
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
                        .clickable(role = Role.Button) { vm.addDish() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.add, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить блюдо", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
                }
            }
        }

        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
            PrimaryButton("Сохранить", onClick = { vm.saveDraft() }, icon = AppIcons.check)
        }
    }

    sourceFor?.let { dish ->
        AlertDialog(
            onDismissRequest = { sourceFor = null },
            title = { Text("Добавить фото") },
            text = { Text("Фото ${dish.photos.size + 1} из $MAX_PHOTOS_PER_DISH") },
            confirmButton = {
                TextButton(onClick = {
                    sourceFor = null
                    val uri = vm.newCameraUri()
                    vm.pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }) { Text("Снять") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sourceFor = null
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Из галереи") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить запись?") },
            text = { Text("${draft.type.title} и все его фото будут удалены.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteDraftMeal() }) {
                    Text("Удалить", color = Palette.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
}

@Composable
private fun TypeSelector(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.segment)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MealType.entries.forEach { t ->
            val sel = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Palette.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.title,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (sel) Palette.accent else Palette.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun DishCard(vm: AppViewModel, dish: DishDraft, index: Int, onAddPhoto: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Фото (до 2)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dish.photos.forEachIndexed { pi, name ->
                Box {
                    PhotoThumb(
                        file = vm.photoFile(name),
                        modifier = Modifier.size(width = 76.dp, height = 68.dp),
                        description = "Фото ${pi + 1}, блюдо ${index + 1}",
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(role = Role.Button) { vm.removePhoto(dish, name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.close, contentDescription = "Убрать фото ${pi + 1}", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (dish.importing) {
                Box(Modifier.size(width = 76.dp, height = 68.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Palette.accent)
                }
            } else if (dish.canAddPhoto) {
                Column(
                    Modifier
                        .size(width = 76.dp, height = 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .dashedBorder(Palette.dashed, 12.dp)
                        .clickable(role = Role.Button, onClick = onAddPhoto),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (dish.photos.isEmpty()) AppIcons.camera else AppIcons.add,
                        contentDescription = null,
                        tint = Palette.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (dish.photos.isEmpty()) "сверху" else "сбоку",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.accent,
                    )
                }
            }
            Text("${dish.photos.size} из $MAX_PHOTOS_PER_DISH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
        }

        // Поля
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Блюдо ${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.muted, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.removeDish(dish) }, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.close, contentDescription = "Удалить блюдо ${index + 1}", tint = Palette.muted, modifier = Modifier.size(18.dp))
                }
            }
            OutlinedTextField(
                value = dish.description,
                onValueChange = { dish.description = it },
                placeholder = { Text("Что за блюдо? Масло, соус, сахар — сколько?", fontSize = 14.sp) },
                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = appFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Вес, г", dish.weight, { dish.weight = it }, Modifier.weight(1f))
                NumberField("Моя оценка, ккал", dish.kcal, { dish.kcal = it }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted, maxLines = 1)
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(5)) },
            placeholder = { Text("—") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(10.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

