package com.hayat.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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

@Composable
fun HayatBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit, onAdd: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        item(current == TopLevel.HOME, Icons.Outlined.Home, stringResource(UiR.string.feature_home)) { onSelect(TopLevel.HOME) }
        item(current == TopLevel.CALENDAR, Icons.Outlined.CalendarMonth, stringResource(UiR.string.feature_calendar)) { onSelect(TopLevel.CALENDAR) }
        item(false, Icons.Outlined.AddCircle, stringResource(UiR.string.feature_add), onAdd)
        item(current == TopLevel.PROGRESS, Icons.Outlined.Insights, stringResource(UiR.string.feature_progress)) { onSelect(TopLevel.PROGRESS) }
        item(current == TopLevel.SETTINGS, Icons.Outlined.Settings, stringResource(UiR.string.feature_settings)) { onSelect(TopLevel.SETTINGS) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.item(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) =
    NavigationBarItem(selected = selected, onClick = onClick, icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1) })

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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.add_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            items(actions) { a ->
                Column(
                    Modifier.heightIn(min = 88.dp).clickable(role = Role.Button) { onNavigate(a.route) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(a.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(a.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
