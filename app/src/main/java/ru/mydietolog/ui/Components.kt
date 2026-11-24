package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.back, contentDescription = "Назад", tint = Palette.ink)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.ink)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit, outlined: Boolean = false) {
    val mod = if (outlined) {
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Palette.surface)
            .border(1.dp, Palette.line, CircleShape)
    } else Modifier.size(44.dp)
    IconButton(onClick = onClick, modifier = mod) {
        Icon(icon, contentDescription = description, tint = Palette.ink)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dark: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Palette.ink else Palette.accent,
            contentColor = Color.White,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(Modifier.fillMaxWidth())
            .height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Palette.surface, contentColor = Palette.ink),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Пунктирная рамка для кнопок «добавить». */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
    )
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius.toPx()))
}

@Composable
fun Dot(on: Boolean, onColor: Color = Palette.accent, offColor: Color = Palette.dotOff, size: Dp = 5.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (on) onColor else offColor),
    )
}

@Composable
fun PhotoThumb(file: File?, modifier: Modifier, radius: Dp = 12.dp, description: String? = null) {
    val shape = RoundedCornerShape(radius)
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(modifier.clip(shape).background(Palette.placeholder), contentAlignment = Alignment.Center) {
            Icon(AppIcons.image, contentDescription = description, tint = Palette.muted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun GoalReadout(sets: Int?, reps: String, weight: String, minutes: Int?) {
    val items = buildList {
        sets?.let { add("Подходы" to it.toString()) }
        if (reps.isNotBlank()) add("Повторы" to reps)
        if (weight.isNotBlank()) add("Вес" to "$weight кг")
        minutes?.let { add("Минуты" to it.toString()) }
    }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, value) ->
            Column {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
            }
        }
    }
}

@Composable
fun appFieldColors(container: Color = Palette.surface) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.accent,
    unfocusedBorderColor = Palette.field,
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    cursorColor = Palette.accent,
)

@Composable
fun InfoBanner(text: String, warning: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (warning) Palette.warnBg else Palette.hintBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            AppIcons.info,
            contentDescription = null,
            tint = if (warning) Palette.warnInk else Palette.accentDark,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (warning) Palette.warnInk else Palette.accentDark,
        )
    }
}
