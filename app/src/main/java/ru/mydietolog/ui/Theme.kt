package ru.mydietolog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val bg = Color(0xFFF4F5F1)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF1A1D1B)
    val inkSoft = Color(0xFF3A403C)
    val muted = Color(0xFF5E6560)
    val line = Color(0xFFE3E6E0)
    val field = Color(0xFFD5DAD2)
    val dashed = Color(0xFF9AA39C)
    val placeholder = Color(0xFFE9ECE6)
    val segment = Color(0xFFE7EAE3)
    val accent = Color(0xFF2F6F4F)
    val accentDark = Color(0xFF1F5038)
    val accentSoft = Color(0xFFE4EFE8)
    val hintBg = Color(0xFFEAF1EC)
    val weekHighlight = Color(0xFFDCEBE2)
    val dotOff = Color(0xFFD3D8D0)
    val warnBg = Color(0xFFFFF3E6)
    val warnInk = Color(0xFF6B3A0F)
    val danger = Color(0xFFB3261E)
}

@Composable
fun MyDietologTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
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
        ),
        content = content,
    )
}
