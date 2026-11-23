package ru.mydietolog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ru.mydietolog.ui.AppViewModel
import java.time.LocalDate
import ru.mydietolog.ui.BottomTabBar
import ru.mydietolog.ui.CustomExerciseScreen
import ru.mydietolog.ui.DayScreen
import ru.mydietolog.reminders.ReminderNotifier
import ru.mydietolog.ui.ExportScreen
import ru.mydietolog.ui.MainTab
import ru.mydietolog.ui.MealEditScreen
import ru.mydietolog.ui.MonthScreen
import ru.mydietolog.ui.MyDietologTheme
import ru.mydietolog.ui.Palette
import ru.mydietolog.ui.ProgramEditScreen
import ru.mydietolog.ui.Screen
import ru.mydietolog.ui.SettingsScreen
import ru.mydietolog.ui.WorkoutPlanScreen
import ru.mydietolog.ui.WorkoutProgramScreen
import ru.mydietolog.ui.WorkoutSessionScreen
import ru.mydietolog.ui.WorkoutsHomeScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openFromIntent(intent)
        setContent {
            MyDietologTheme {
                val screen = vm.current
                val showTabs = screen is Screen.Day || screen is Screen.WorkoutsHome

                Column(Modifier.fillMaxSize().background(Palette.bg)) {
                    Box(Modifier.weight(1f)) {
                        BackHandler(enabled = screen !is Screen.Day || vm.tab == MainTab.WORKOUTS) {
                            when {
                                vm.back() -> Unit
                                vm.tab == MainTab.WORKOUTS -> vm.selectTab(MainTab.DIARY)
                                else -> finish()
                            }
                        }
                        when (screen) {
                            Screen.Day -> DayScreen(vm)
                            Screen.Edit -> MealEditScreen(vm)
                            is Screen.Month -> MonthScreen(vm, screen.month)
                            is Screen.Export -> ExportScreen(vm, screen.weekStart)
                            Screen.WorkoutsHome -> WorkoutsHomeScreen(vm)
                            Screen.WorkoutSession -> WorkoutSessionScreen(vm)
                            is Screen.WorkoutPlan -> WorkoutPlanScreen(vm, screen.date)
                            Screen.WorkoutProgram -> WorkoutProgramScreen(vm)
                            Screen.ProgramEdit -> ProgramEditScreen(vm)
                            Screen.CustomExercise -> CustomExerciseScreen(vm)
                            Screen.Settings -> SettingsScreen(vm)
                        }
                    }
                    if (showTabs) {
                        BottomTabBar(tab = vm.tab, onSelect = { vm.selectTab(it) })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent?) {
        val date = intent?.getStringExtra(ReminderNotifier.EXTRA_OPEN_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        vm.openDay(date)
    }
}
