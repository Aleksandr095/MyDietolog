package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomTabBar(tab: MainTab, onSelect: (MainTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.surface)) {
        HorizontalDivider(color = Palette.line)
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            TabItem("Дневник", tab == MainTab.DIARY, Modifier.weight(1f)) { onSelect(MainTab.DIARY) }
            TabItem("Тренировки", tab == MainTab.WORKOUTS, Modifier.weight(1f)) { onSelect(MainTab.WORKOUTS) }
        }
    }
}

@Composable
private fun TabItem(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Palette.muted,
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) Palette.accent else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
