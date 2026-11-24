package com.hayat.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hayat.core.designsystem.component.animateEnter
import com.hayat.core.designsystem.component.pressScale
import com.hayat.core.designsystem.theme.HayatThemeExt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hayat.core.navigation.*
import com.hayat.core.ui.R as UiR

/**
 * Floating rounded navigation bar: icon tiles, the active one filled (white on dark, ink on light),
 * and a green "+" tile in the centre. Labels are announced to screen readers.
 */
@Composable
fun HayatBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit, onAdd: () -> Unit) {
    val ext = HayatThemeExt.colors
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(72.dp)
                .shadow(if (ext.isDark) 0.dp else 14.dp, shape, ambientColor = Color.Black.copy(0.08f), spotColor = Color.Black.copy(0.14f))
                .clip(shape)
                .background(if (ext.isDark) Color(0xF2172133) else MaterialTheme.colorScheme.surface)
                .border(1.dp, ext.hairline, shape)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTile(current == TopLevel.HOME, Icons.Outlined.Home, stringResource(UiR.string.feature_home)) { onSelect(TopLevel.HOME) }
            NavTile(current == TopLevel.CALENDAR, Icons.Outlined.CalendarMonth, stringResource(UiR.string.feature_calendar)) { onSelect(TopLevel.CALENDAR) }
            AddTile(stringResource(UiR.string.feature_add), onAdd)
            NavTile(current == TopLevel.PROGRESS, Icons.Outlined.Insights, stringResource(UiR.string.feature_progress)) { onSelect(TopLevel.PROGRESS) }
            NavTile(current == TopLevel.SETTINGS, Icons.Outlined.Settings, stringResource(UiR.string.feature_settings)) { onSelect(TopLevel.SETTINGS) }
        }
    }
}

@Composable
private fun NavTile(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ext = HayatThemeExt.colors
    val interaction = remember { MutableInteractionSource() }
    val activeBg = if (ext.isDark) Color.White else scheme.onSurface
    val bg by animateColorAsState(if (selected) activeBg else Color.Transparent, tween(180), label = "tile")
    val fg by animateColorAsState(if (selected) (if (ext.isDark) Color(0xFF0B1220) else scheme.surface) else scheme.onSurfaceVariant, tween(180), label = "tilefg")
    Box(
        Modifier.size(width = 58.dp, height = 52.dp).pressScale(interaction).clip(RoundedCornerShape(18.dp)).background(bg)
            .selectable(selected, interaction, ripple(), role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun AddTile(label: String, onClick: () -> Unit) {
    val ext = HayatThemeExt.colors
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(width = 64.dp, height = 52.dp).pressScale(interaction, 0.92f).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(ext.gradientStart, ext.gradientEnd)))
            .clickable(interaction, ripple(color = Color.White), role = Role.Button) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey); onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, label, tint = Color.White, modifier = Modifier.size(30.dp)) }
}

private data class AddAction(val icon: ImageVector, val label: Int, val route: Any)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onNavigate: (Any) -> Unit, islamic: Boolean) {
    val ext = HayatThemeExt.colors
    // The five daily trackers first, as large tiles.
    val quick = buildList {
        add(Triple(AddAction(Icons.Outlined.WaterDrop, R.string.add_water, WaterRoute), ext.water, 0))
        add(Triple(AddAction(Icons.Outlined.Repeat, R.string.add_habit, HabitEditRoute()), ext.habits, 1))
        if (islamic) add(Triple(AddAction(Icons.Outlined.Mosque, R.string.add_prayer, IbadahRoute), ext.prayer, 2))
        add(Triple(AddAction(Icons.Outlined.Timer, R.string.add_focus, FocusRoute), ext.mint, 3))
        add(Triple(AddAction(Icons.Outlined.FitnessCenter, R.string.add_workout, FitnessRoute), ext.pink, 4))
    }
    val more = listOf(
        AddAction(Icons.Outlined.TaskAlt, R.string.add_task, TaskEditRoute()),
        AddAction(Icons.Outlined.Alarm, R.string.add_reminder, ReminderEditRoute()),
        AddAction(Icons.Outlined.ViewTimeline, R.string.add_routine, RoutineEditRoute()),
        AddAction(Icons.Outlined.Medication, R.string.add_medicine, MedicineEditRoute()),
        AddAction(Icons.Outlined.StickyNote2, R.string.add_note, NoteEditRoute()),
        AddAction(Icons.Outlined.Book, R.string.add_journal, JournalEditRoute()),
        AddAction(Icons.Outlined.Cake, R.string.add_event, EventEditRoute()),
        AddAction(Icons.Outlined.Payments, R.string.add_finance, FinanceEditRoute()),
        AddAction(Icons.Outlined.Bedtime, R.string.add_sleep, SleepRoute),
        AddAction(Icons.Outlined.Restaurant, R.string.add_meal, MealPlanEditRoute()),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.add_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }
            quick.forEach { (a, tone, i) ->
                item {
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier.animateEnter(i).pressScale(interaction).clip(MaterialTheme.shapes.medium)
                            .clickable(interaction, ripple(), role = Role.Button) { onNavigate(a.route) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(tone.first), contentAlignment = Alignment.Center) { Icon(a.icon, null, tint = tone.second) }
                        Text(stringResource(a.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.add_more), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
            items(more.size) { i ->
                val a = more[i]
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier.heightIn(min = 76.dp).pressScale(interaction).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(interaction, ripple(), role = Role.Button) { onNavigate(a.route) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(a.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(a.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
