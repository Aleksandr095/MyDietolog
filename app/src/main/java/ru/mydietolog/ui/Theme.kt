package ru.mydietolog.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme { SYSTEM, LIGHT, DARK }

fun resolveDark(choice: AppTheme, systemDark: Boolean): Boolean = when (choice) {
    AppTheme.DARK -> true
    AppTheme.LIGHT -> false
    AppTheme.SYSTEM -> systemDark
}

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _choice = MutableStateFlow(read())
    val choice: StateFlow<AppTheme> = _choice.asStateFlow()

    fun set(value: AppTheme) {
        prefs.edit().putString("theme", value.name).apply()
        _choice.value = value
    }

    private fun read(): AppTheme = runCatching {
        AppTheme.valueOf(prefs.getString("theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name)
    }.getOrDefault(AppTheme.SYSTEM)
}

private data class PaletteSet(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val line: Color,
    val field: Color,
    val dashed: Color,
    val placeholder: Color,
    val segment: Color,
    val accent: Color,
    val accentDark: Color,
    val accentSoft: Color,
    val hintBg: Color,
    val weekHighlight: Color,
    val dotOff: Color,
    val warnBg: Color,
    val warnInk: Color,
    val danger: Color,
)

private val Light = PaletteSet(
    bg = Color(0xFFF4F5F1),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1D1B),
    inkSoft = Color(0xFF3A403C),
    muted = Color(0xFF5E6560),
    line = Color(0xFFE3E6E0),
    field = Color(0xFFD5DAD2),
    dashed = Color(0xFF9AA39C),
    placeholder = Color(0xFFE9ECE6),
    segment = Color(0xFFE7EAE3),
    accent = Color(0xFF2F6F4F),
    accentDark = Color(0xFF1F5038),
    accentSoft = Color(0xFFE4EFE8),
    hintBg = Color(0xFFEAF1EC),
    weekHighlight = Color(0xFFDCEBE2),
    dotOff = Color(0xFFD3D8D0),
    warnBg = Color(0xFFFFF3E6),
    warnInk = Color(0xFF6B3A0F),
    danger = Color(0xFFB3261E),
)

private val Dark = PaletteSet(
    bg = Color(0xFF121614),
    surface = Color(0xFF1C221E),
    ink = Color(0xFFF3F6F2),
    inkSoft = Color(0xFFD5DDD6),
    muted = Color(0xFFA3ADA6),
    line = Color(0xFF2E3631),
    field = Color(0xFF3A433D),
    dashed = Color(0xFF7D877F),
    placeholder = Color(0xFF2A312C),
    segment = Color(0xFF242B27),
    accent = Color(0xFF2F6F4F),
    accentDark = Color(0xFFB7E0CC),
    accentSoft = Color(0xFF1E3328),
    hintBg = Color(0xFF1A2B22),
    weekHighlight = Color(0xFF24382E),
    dotOff = Color(0xFF3A433D),
    warnBg = Color(0xFF3A2A18),
    warnInk = Color(0xFFFFD7A8),
    danger = Color(0xFFFF8A80),
)

object Palette {
    var bg by mutableStateOf(Light.bg)
        private set
    var surface by mutableStateOf(Light.surface)
        private set
    var ink by mutableStateOf(Light.ink)
        private set
    var inkSoft by mutableStateOf(Light.inkSoft)
        private set
    var muted by mutableStateOf(Light.muted)
        private set
    var line by mutableStateOf(Light.line)
        private set
    var field by mutableStateOf(Light.field)
        private set
    var dashed by mutableStateOf(Light.dashed)
        private set
    var placeholder by mutableStateOf(Light.placeholder)
        private set
    var segment by mutableStateOf(Light.segment)
        private set
    var accent by mutableStateOf(Light.accent)
        private set
    var accentDark by mutableStateOf(Light.accentDark)
        private set
    var accentSoft by mutableStateOf(Light.accentSoft)
        private set
    var hintBg by mutableStateOf(Light.hintBg)
        private set
    var weekHighlight by mutableStateOf(Light.weekHighlight)
        private set
    var dotOff by mutableStateOf(Light.dotOff)
        private set
    var warnBg by mutableStateOf(Light.warnBg)
        private set
    var warnInk by mutableStateOf(Light.warnInk)
        private set
    var danger by mutableStateOf(Light.danger)
        private set

    fun apply(dark: Boolean) {
        val source = if (dark) Dark else Light
        bg = source.bg
        surface = source.surface
        ink = source.ink
        inkSoft = source.inkSoft
        muted = source.muted
        line = source.line
        field = source.field
        dashed = source.dashed
        placeholder = source.placeholder
        segment = source.segment
        accent = source.accent
        accentDark = source.accentDark
        accentSoft = source.accentSoft
        hintBg = source.hintBg
        weekHighlight = source.weekHighlight
        dotOff = source.dotOff
        warnBg = source.warnBg
        warnInk = source.warnInk
        danger = source.danger
    }
}

@Composable
fun MyDietologTheme(dark: Boolean, content: @Composable () -> Unit) {
    SideEffect { Palette.apply(dark) }
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                primary = Palette.accent,
                onPrimary = Color.White,
                primaryContainer = Palette.accentSoft,
                onPrimaryContainer = Palette.accentDark,
                background = Palette.bg,
                onBackground = Palette.ink,
                surface = Palette.surface,
                onSurface = Palette.ink,
                surfaceVariant = Palette.segment,
                onSurfaceVariant = Palette.muted,
                outline = Palette.field,
                error = Palette.danger,
            )
        } else {
            lightColorScheme(
                primary = Palette.accent,
                onPrimary = Color.White,
                primaryContainer = Palette.accentSoft,
                onPrimaryContainer = Palette.accentDark,
                background = Palette.bg,
                onBackground = Palette.ink,
                surface = Palette.surface,
                onSurface = Palette.ink,
                surfaceVariant = Palette.segment,
                onSurfaceVariant = Palette.muted,
                outline = Palette.field,
                error = Palette.danger,
            )
        },
        content = content,
    )
}
