package ru.mydietolog.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ru.mydietolog.data.DiaryReminder
import ru.mydietolog.data.MAX_DIARY_REMINDERS
import ru.mydietolog.data.MealType
import ru.mydietolog.data.WATER_INTERVAL_OPTIONS_HOURS
import ru.mydietolog.reminders.ReminderNotifier

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.reminders.collectAsState()
    val context = LocalContext.current

    var notificationsAllowed by remember { mutableStateOf(ReminderNotifier.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = ReminderNotifier.canNotify(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = ReminderNotifier.canNotify(context)
    }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenHeader(title = "Настройки", onBack = { vm.back() })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notificationsAllowed) {
                InfoBanner("Уведомления выключены — напоминания не будут приходить.", warning = true)
                if (Build.VERSION.SDK_INT >= 33) {
                    SecondaryButton("Разрешить уведомления", onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
                SecondaryButton("Открыть настройки уведомлений", onClick = {
                    context.startActivity(notificationSettingsIntent(context))
                })
            }

            SettingsCard(
                title = "Напоминания о дневнике",
                checked = s.diaryEnabled,
                onCheckedChange = { on -> vm.updateReminders { it.copy(diaryEnabled = on) } },
            ) {
                Text(
                    "Если приём уже записан, напоминание не придёт. Общее напоминание молчит, когда записаны завтрак, обед и ужин.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Palette.muted,
                )
                s.diaryReminders.forEachIndexed { index, reminder ->
                    DiaryReminderRow(
                        reminder = reminder,
                        onChange = { changed ->
                            vm.updateReminders { cfg ->
                                cfg.copy(diaryReminders = cfg.diaryReminders.mapIndexed { i, r -> if (i == index) changed else r })
                            }
                        },
                        onRemove = {
                            vm.updateReminders { cfg ->
                                cfg.copy(diaryReminders = cfg.diaryReminders.filterIndexed { i, _ -> i != index })
                            }
                        },
                    )
                }
                if (s.diaryReminders.size < MAX_DIARY_REMINDERS) {
                    SecondaryButton("+ Напоминание", onClick = {
                        vm.updateReminders { cfg -> cfg.copy(diaryReminders = cfg.diaryReminders + DiaryReminder(12 * 60)) }
                    })
                }
            }

            SettingsCard(
                title = "Напоминания пить воду",
                checked = s.waterEnabled,
                onCheckedChange = { on -> vm.updateReminders { it.copy(waterEnabled = on) } },
            ) {
                Text("Интервал", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WATER_INTERVAL_OPTIONS_HOURS.forEach { hours ->
                        ChoiceChip("$hours ч", selected = s.waterIntervalHours == hours) {
                            vm.updateReminders { it.copy(waterIntervalHours = hours) }
                        }
                    }
                }
                Text("Окно времени", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("с", fontSize = 14.sp, color = Palette.inkSoft)
                    ChoiceChip(formatMinute(s.waterFromMinute), selected = false) {
                        pickTime(context, s.waterFromMinute) { m -> vm.updateReminders { it.copy(waterFromMinute = m) } }
                    }
                    Text("до", fontSize = 14.sp, color = Palette.inkSoft)
                    ChoiceChip(formatMinute(s.waterToMinute), selected = false) {
                        pickTime(context, s.waterToMinute) { m -> vm.updateReminders { it.copy(waterToMinute = m) } }
                    }
                }
                if (s.waterToMinute <= s.waterFromMinute) {
                    InfoBanner("Окно пустое: конец должен быть позже начала.", warning = true)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Palette.ink,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Palette.accent,
                ),
            )
        }
        if (checked) content()
    }
}

@Composable
private fun DiaryReminderRow(
    reminder: DiaryReminder,
    onChange: (DiaryReminder) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(formatMinute(reminder.minuteOfDay), selected = false) {
            pickTime(context, reminder.minuteOfDay) { m -> onChange(reminder.copy(minuteOfDay = m)) }
        }
        Box(Modifier.weight(1f)) {
            ChoiceChip(reminder.meal?.title ?: "Весь день", selected = false) { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Весь день") },
                    onClick = {
                        menuOpen = false
                        onChange(reminder.copy(meal = null))
                    },
                )
                MealType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.title) },
                        onClick = {
                            menuOpen = false
                            onChange(reminder.copy(meal = type))
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = "Удалить напоминание" },
        ) {
            Icon(AppIcons.close, contentDescription = null, tint = Palette.muted)
        }
    }
}

private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private fun pickTime(context: Context, minute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, hour, min -> onPicked(hour * 60 + min) }, minute / 60, minute % 60, true).show()
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
