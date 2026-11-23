package ru.mydietolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            thickness = 2.dp,
            color = if (selected) Palette.accent else Palette.surface,
        )
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Palette.accent else Palette.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
