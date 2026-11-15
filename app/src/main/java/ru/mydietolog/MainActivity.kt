package ru.mydietolog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ru.mydietolog.ui.AppViewModel
import ru.mydietolog.ui.DayScreen
import ru.mydietolog.ui.ExportScreen
import ru.mydietolog.ui.MealEditScreen
import ru.mydietolog.ui.MonthScreen
import ru.mydietolog.ui.MyDietologTheme
import ru.mydietolog.ui.Palette
import ru.mydietolog.ui.Screen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyDietologTheme {
                Box(Modifier.fillMaxSize().background(Palette.bg)) {
                    val screen = vm.current
                    BackHandler(enabled = vm.stack.size > 1 || screen is Screen.Edit) { vm.back() }
                    when (screen) {
                        Screen.Day -> DayScreen(vm)
                        Screen.Edit -> MealEditScreen(vm)
                        is Screen.Month -> MonthScreen(vm, screen.month)
                        is Screen.Export -> ExportScreen(vm, screen.weekStart)
                    }
                }
            }
        }
    }
}
