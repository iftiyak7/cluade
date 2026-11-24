package com.hayat.app

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
 * Floating pill navigation: the active destination expands into a filled pill with its label,
 * the others are icon buttons; Add sits in the middle as a raised gradient button.
 */
@Composable
fun HayatBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit, onAdd: () -> Unit) {
    val ext = HayatThemeExt.colors
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(0.18f), spotColor = Color.Black.copy(0.25f))
                .clip(RoundedCornerShape(50)).background(ext.ink).border(1.dp, ext.hairline, RoundedCornerShape(50)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavPill(current == TopLevel.HOME, Icons.Outlined.Home, stringResource(UiR.string.feature_home)) { onSelect(TopLevel.HOME) }
            NavPill(current == TopLevel.CALENDAR, Icons.Outlined.CalendarMonth, stringResource(UiR.string.feature_calendar)) { onSelect(TopLevel.CALENDAR) }
            AddButton(stringResource(UiR.string.feature_add), onAdd)
            NavPill(current == TopLevel.PROGRESS, Icons.Outlined.Insights, stringResource(UiR.string.feature_progress)) { onSelect(TopLevel.PROGRESS) }
            NavPill(current == TopLevel.SETTINGS, Icons.Outlined.Settings, stringResource(UiR.string.feature_settings)) { onSelect(TopLevel.SETTINGS) }
        }
    }
}

@Composable
private fun NavPill(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val ext = HayatThemeExt.colors
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) ext.inkAccent else Color.Transparent, label = "navbg")
    val fg by animateColorAsState(if (selected) ext.ink else ext.onInk.copy(alpha = 0.75f), label = "navfg")
    Row(
        Modifier.height(52.dp).pressScale(interaction).clip(RoundedCornerShape(50)).background(bg)
            .selectable(selected, interaction, ripple(), role = Role.Tab, onClick = onClick)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = if (selected) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (selected) null else label, tint = fg)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    val ext = HayatThemeExt.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(52.dp).pressScale(interaction, 0.9f).clip(CircleShape)
            .background(Brush.linearGradient(listOf(ext.gradientStart, ext.gradientEnd)))
            .clickable(interaction, ripple(color = Color.White), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, label, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

private data class AddAction(val icon: ImageVector, val label: Int, val route: Any)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onNavigate: (Any) -> Unit, islamic: Boolean) {
    val actions = buildList {
        add(AddAction(Icons.Outlined.TaskAlt, R.string.add_task, TaskEditRoute()))
        add(AddAction(Icons.Outlined.Alarm, R.string.add_reminder, ReminderEditRoute()))
        add(AddAction(Icons.Outlined.ViewTimeline, R.string.add_routine, RoutineEditRoute()))
        add(AddAction(Icons.Outlined.WaterDrop, R.string.add_water, WaterRoute))
        add(AddAction(Icons.Outlined.Medication, R.string.add_medicine, MedicineEditRoute()))
        add(AddAction(Icons.Outlined.Repeat, R.string.add_habit, HabitEditRoute()))
        add(AddAction(Icons.Outlined.StickyNote2, R.string.add_note, NoteEditRoute()))
        add(AddAction(Icons.Outlined.Book, R.string.add_journal, JournalEditRoute()))
        add(AddAction(Icons.Outlined.Cake, R.string.add_event, EventEditRoute()))
        add(AddAction(Icons.Outlined.Payments, R.string.add_finance, FinanceEditRoute()))
        add(AddAction(Icons.Outlined.Bedtime, R.string.add_sleep, SleepRoute))
        add(AddAction(Icons.Outlined.Restaurant, R.string.add_meal, MealPlanEditRoute()))
    }
    val ext = HayatThemeExt.colors
    val tones = listOf(ext.mint, ext.lavender, ext.amber, ext.sky, ext.pink)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(stringResource(R.string.add_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(actions) { i, a ->
                val tone = tones[i % tones.size]
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier.animateEnter(i).heightIn(min = 92.dp).pressScale(interaction).clip(MaterialTheme.shapes.large).background(tone.first)
                        .clickable(interaction, ripple(), role = Role.Button) { onNavigate(a.route) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(a.icon, null, Modifier.size(28.dp), tint = tone.second)
                    Text(stringResource(a.label), style = MaterialTheme.typography.labelMedium, color = tone.second, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                }
            }
        }
    }
}
