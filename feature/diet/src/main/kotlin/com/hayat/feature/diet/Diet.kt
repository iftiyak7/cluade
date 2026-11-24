package com.hayat.feature.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.health.MealUseCases
import com.hayat.core.domain.repository.MealRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.MealLog
import com.hayat.core.model.MealPlan
import com.hayat.core.model.MealStatus
import com.hayat.core.model.MealType
import com.hayat.core.model.ShoppingItem
import com.hayat.core.navigation.DietRoute
import com.hayat.core.navigation.MealPlanEditRoute
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.WeekdayPicker
import com.hayat.core.ui.forField
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class DietState(
    val loading: Boolean = true,
    val todayPlans: List<MealPlan> = emptyList(),
    val plans: List<MealPlan> = emptyList(),
    val todayLogs: List<MealLog> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val weekPlanned: Int = 0,
    val weekEaten: Int = 0,
)

@HiltViewModel
class DietViewModel @Inject constructor(
    private val meals: MealRepository,
    private val useCases: MealUseCases,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(meals.observePlans(), meals.observeLogs(today.minusDays(6), today), meals.observeShopping()) { plans, logs, shop ->
        val active = plans.filter { !it.isArchived }
        val planned = (0L..6L).sumOf { o -> active.count { today.minusDays(o).dayOfWeek in it.weekdays } }
        DietState(false, active.filter { today.dayOfWeek in it.weekdays }.sortedBy { it.time }, active, logs.filter { it.date == today }, shop,
            planned, logs.count { it.status == MealStatus.EATEN && it.mealPlanId != null })
    }.stateInVm(DietState())

    fun log(plan: MealPlan, status: MealStatus) = safeLaunch { useCases.log(plan, today, status, "", null).handle { message(R.string.diet_logged) } }
    fun logExtra(type: MealType, description: String, calories: Int?) = safeLaunch { useCases.log(null, today, MealStatus.EATEN, description, calories, type).handle { message(R.string.diet_logged) } }
    fun deleteLog(log: MealLog) = safeLaunch { meals.deleteLog(log.id) }
    fun addShopping(title: String, qty: String) = safeLaunch {
        if (title.isBlank()) return@safeLaunch
        meals.saveShoppingItem(ShoppingItem(ids.newId(), title.trim().take(120), qty.trim().take(40), false, state.value.shopping.size, time.now()))
    }
    fun toggleShopping(i: ShoppingItem) = safeLaunch { meals.saveShoppingItem(i.copy(isChecked = !i.isChecked)) }
    fun deleteShopping(i: ShoppingItem) = safeLaunch { meals.deleteShoppingItem(i.id) }
    fun clearChecked() = safeLaunch { meals.clearCheckedShopping() }
}

data class MealForm(val plan: MealPlan, val isNew: Boolean, val caloriesText: String, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class MealPlanEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val meals: MealRepository,
    private val useCases: MealUseCases,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<MealPlanEditRoute>()
    private val _form = MutableStateFlow<MealForm?>(null)
    val form = _form.asStateFlow()
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)
    init {
        safeLaunch {
            val now = time.now()
            val p = route.id?.let { meals.getPlan(it) }
            _form.value = p?.let { MealForm(it, false, it.calories?.toString().orEmpty()) }
                ?: MealForm(MealPlan(ids.newId(), "", MealType.BREAKFAST, LocalTime.of(8, 0), createdAt = now, updatedAt = now), true, "")
        }
    }
    fun edit(t: (MealPlan) -> MealPlan) = _form.update { it?.copy(plan = t(it.plan), errors = emptyList()) }
    fun editCalories(v: String) = _form.update { it?.copy(caloriesText = v) }
    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val errors = useCases.savePlan(f.plan.copy(calories = f.caloriesText.toIntOrNull())).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
    fun delete() = safeLaunch { _form.value?.let { useCases.deletePlan(it.plan.id).handle { emit(UiEvent.NavigateBack) } } }
}

fun NavGraphBuilder.dietGraph(nav: NavController) {
    composable<DietRoute> { DietScreen(nav) }
    composable<MealPlanEditRoute> { MealPlanEditScreen(nav::popBackStack) }
}

@Composable
private fun DietScreen(nav: NavController, vm: DietViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var extra by remember { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.diet_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { if (tab == 1) ExtendedFloatingActionButton(onClick = { nav.navigate(MealPlanEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.diet_new_plan)) }) }) { pad ->
        Column(Modifier.padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.diet_tab_today, R.string.diet_tab_plan, R.string.diet_tab_shopping, R.string.diet_tab_history).forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(stringResource(t), maxLines = 1) }) }
            }
            if (s.loading) { LoadingBox(); return@Column }
            LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        if (s.todayPlans.isEmpty()) item { InfoBanner(stringResource(R.string.diet_no_plans)) }
                        items(s.todayPlans, key = { it.id }) { p ->
                            val log = s.todayLogs.firstOrNull { it.mealPlanId == p.id }
                            HayatCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                                        Text(fmt.time(p.time) + " · " + stringResource(p.mealType.labelRes()) + (p.calories?.let { " · " + stringResource(R.string.diet_kcal, fmt.number(it)) } ?: ""), style = MaterialTheme.typography.bodySmall)
                                    }
                                    log?.let { StatusChip(stringResource(if (it.status == MealStatus.EATEN) R.string.diet_eaten else R.string.diet_skipped), if (it.status == MealStatus.EATEN) StatusTone.SUCCESS else StatusTone.NEUTRAL) }
                                }
                                if (log == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { vm.log(p, MealStatus.EATEN) }) { Text(stringResource(R.string.diet_eaten)) }
                                    OutlinedButton(onClick = { vm.log(p, MealStatus.SKIPPED) }) { Text(stringResource(R.string.diet_skipped)) }
                                } else TextButton(onClick = { vm.deleteLog(log) }) { Text(stringResource(UiR.string.action_undo)) }
                            }
                        }
                        item { TextButton(onClick = { extra = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.diet_log_extra)) } }
                        items(s.todayLogs.filter { it.mealPlanId == null }, key = { it.id }) { l ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(l.mealType.labelRes()) + " · " + l.description, Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteLog(l) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                            }
                        }
                    }
                    1 -> {
                        if (s.plans.isEmpty()) item { EmptyState(Icons.Outlined.Restaurant, stringResource(R.string.diet_tab_plan), stringResource(R.string.diet_plan_empty)) }
                        items(s.plans, key = { it.id }) { p ->
                            HayatCard(onClick = { nav.navigate(MealPlanEditRoute(p.id)) }) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(fmt.time(p.time) + " · " + stringResource(p.mealType.labelRes()) + " · " + p.weekdays.days.joinToString(", ") { fmt.weekdayShort(it) }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    2 -> {
                        item { ShoppingInput(vm::addShopping) }
                        if (s.shopping.isEmpty()) item { Text(stringResource(R.string.diet_shopping_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(s.shopping, key = { it.id }) { i ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(i.isChecked, { vm.toggleShopping(i) })
                                Text(i.title + if (i.quantity.isNotBlank()) " · " + i.quantity else "", Modifier.weight(1f), textDecoration = if (i.isChecked) TextDecoration.LineThrough else null)
                                IconButton(onClick = { vm.deleteShopping(i) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                            }
                        }
                        if (s.shopping.any { it.isChecked }) item { TextButton(onClick = vm::clearChecked) { Text(stringResource(R.string.diet_shopping_clear)) } }
                    }
                    3 -> item { HayatCard { Text(fmt.digits(stringResource(R.string.diet_history_summary, s.weekEaten, s.weekPlanned)), style = MaterialTheme.typography.bodyLarge) } }
                }
            }
        }
    }
    if (extra) ExtraMealDialog(onDismiss = { extra = false }) { type, desc, cal -> vm.logExtra(type, desc, cal); extra = false }
}

@Composable
private fun ShoppingInput(onAdd: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, Modifier.weight(2f), label = { Text(stringResource(R.string.diet_shopping_add)) }, singleLine = true)
        OutlinedTextField(qty, { qty = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.diet_shopping_qty)) }, singleLine = true)
        IconButton(onClick = { onAdd(title, qty); title = ""; qty = "" }, enabled = title.isNotBlank()) { Icon(Icons.Outlined.AddCircle, stringResource(UiR.string.action_add)) }
    }
}

@Composable
private fun ExtraMealDialog(onDismiss: () -> Unit, onSave: (MealType, String, Int?) -> Unit) {
    var type by remember { mutableStateOf(MealType.SNACK) }
    var desc by remember { mutableStateOf("") }
    var cal by remember { mutableStateOf("") }
    val labels = MealType.entries.associateWith { stringResource(it.labelRes()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.diet_log_extra)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChips(MealType.entries, type, { labels.getValue(it) }, { type = it })
                OutlinedTextField(desc, { desc = it.take(200) }, label = { Text(stringResource(R.string.diet_what)) })
                NumberField(cal, { cal = it }, stringResource(R.string.diet_calories))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(type, desc, cal.toIntOrNull()) }) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } })
}

@Composable
private fun MealPlanEditScreen(onBack: () -> Unit, vm: MealPlanEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) R.string.diet_new_plan else R.string.diet_edit_plan), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val p = f.plan
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(p.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(UiR.string.label_name), error = f.errors.forField("name"), maxLength = 120)
            val labels = MealType.entries.associateWith { stringResource(it.labelRes()) }
            Text(stringResource(R.string.diet_meal_type), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(MealType.entries, p.mealType, { labels.getValue(it) }, { t -> vm.edit { it.copy(mealType = t) } })
            TimeField(stringResource(UiR.string.label_time), p.time, { t -> t?.let { vm.edit { s -> s.copy(time = it) } } })
            Text(stringResource(UiR.string.label_days), style = MaterialTheme.typography.titleSmall)
            WeekdayPicker(p.weekdays, { w -> vm.edit { it.copy(weekdays = w) } }, firstDay)
            if (f.errors.forField("weekdays") != null) Text(stringResource(UiR.string.error_empty_selection), color = MaterialTheme.colorScheme.error)
            FormTextField(p.description, { v -> vm.edit { it.copy(description = v) } }, stringResource(UiR.string.label_description), singleLine = false)
            NumberField(f.caloriesText, vm::editCalories, stringResource(R.string.diet_calories), error = f.errors.forField("calories"))
            InfoBanner(stringResource(R.string.diet_calories_note))
            SwitchRow(stringResource(R.string.diet_reminder), p.reminderEnabled, { v -> vm.edit { it.copy(reminderEnabled = v) } })
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete() }, { confirm = false }, destructive = true)
}

