package com.hayat.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.hayat.core.designsystem.component.ColorSwatchPicker
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.HeatmapRow
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.theme.UserColorChoices
import com.hayat.core.domain.productivity.HabitStatsCalculator
import com.hayat.core.domain.productivity.HabitUseCases
import com.hayat.core.domain.repository.HabitRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.Habit
import com.hayat.core.model.HabitCompletion
import com.hayat.core.model.HabitKind
import com.hayat.core.model.HabitStats
import com.hayat.core.navigation.HabitEditRoute
import com.hayat.core.navigation.HabitsRoute
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class HabitItem(val habit: Habit, val today: Int, val scheduledToday: Boolean, val stats: HabitStats, val heat: List<Float>)
data class HabitsState(val loading: Boolean = true, val items: List<HabitItem> = emptyList())

@HiltViewModel
class HabitsViewModel @Inject constructor(
    habits: HabitRepository,
    private val useCases: HabitUseCases,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(habits.observeHabits(), habits.observeCompletions(today.minusDays(34), today)) { list, completions ->
        HabitsState(false, list.filter { !it.isArchived }.map { h -> item(h, completions) })
    }.stateInVm(HabitsState())

    private fun item(h: Habit, c: List<HabitCompletion>): HabitItem {
        val created = h.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val stats = HabitStatsCalculator.stats(h, c, maxOf(today.minusDays(29), created), today)
        val counts = c.filter { it.habitId == h.id }.associate { it.date to it.count }
        val heat = (34 downTo 0).map { o ->
            val d = today.minusDays(o.toLong())
            when {
                d.isBefore(created) || d.dayOfWeek !in h.weekdays -> 0f
                h.isSuccess(counts[d] ?: 0) && (h.kind == HabitKind.BUILD || d != today) -> 1f
                (counts[d] ?: 0) > 0 && h.kind == HabitKind.BUILD -> 0.4f
                else -> 0f
            }
        }
        return HabitItem(h, counts[today] ?: 0, today.dayOfWeek in h.weekdays, stats, heat)
    }

    fun increment(h: Habit, delta: Int) = safeLaunch { useCases.increment(h.id, today, delta).handle() }
    fun setPaused(h: Habit) = safeLaunch { useCases.setPaused(h, !h.isPaused).handle() }
    fun archive(h: Habit) = safeLaunch { useCases.setArchived(h, true).handle() }
    fun delete(h: Habit) = safeLaunch { useCases.delete(h.id).handle() }
}

data class HabitForm(val habit: Habit, val isNew: Boolean, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val habits: HabitRepository,
    private val useCases: HabitUseCases,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<HabitEditRoute>()
    private val _form = MutableStateFlow<HabitForm?>(null)
    val form = _form.asStateFlow()
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)
    init {
        safeLaunch {
            val now = time.now()
            _form.value = route.id?.let { habits.getHabit(it) }?.let { HabitForm(it, false) }
                ?: HabitForm(Habit(ids.newId(), "", color = UserColorChoices.first(), createdAt = now, updatedAt = now), true)
        }
    }
    fun edit(t: (Habit) -> Habit) = _form.update { it?.copy(habit = t(it.habit), errors = emptyList()) }
    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val errors = useCases.save(f.habit).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
}

fun NavGraphBuilder.habitsGraph(nav: NavController) {
    composable<HabitsRoute> { HabitsScreen(nav) }
    composable<HabitEditRoute> { HabitEditScreen(nav::popBackStack) }
}

@Composable
private fun HabitsScreen(nav: NavController, vm: HabitsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    HayatScreen(title = stringResource(R.string.hb_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(HabitEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.hb_new)) }) }) { pad ->
        when {
            s.loading -> LoadingBox(Modifier.padding(pad))
            s.items.isEmpty() -> EmptyState(Icons.Outlined.Repeat, stringResource(R.string.hb_empty_title), stringResource(R.string.hb_empty_text), Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.items, key = { it.habit.id }) { item -> HabitCard(item, vm, onEdit = { nav.navigate(HabitEditRoute(item.habit.id)) }) }
                item { InfoBanner(stringResource(R.string.hb_paused_note)) }
            }
        }
    }
}

@Composable
private fun HabitCard(item: HabitItem, vm: HabitsViewModel, onEdit: () -> Unit) {
    val h = item.habit
    val fmt = LocalFormatter.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    HayatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(h.color)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (h.kind == HabitKind.BUILD) R.string.hb_build else R.string.hb_reduce), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (h.isPaused) StatusChip(stringResource(UiR.string.label_paused), StatusTone.NEUTRAL, icon = Icons.Outlined.PauseCircle)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(UiR.string.action_more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(UiR.string.action_edit)) }, { menu = false; onEdit() })
                    DropdownMenuItem({ Text(stringResource(if (h.isPaused) UiR.string.action_resume else UiR.string.action_pause)) }, { menu = false; vm.setPaused(h) })
                    DropdownMenuItem({ Text(stringResource(UiR.string.action_archive)) }, { menu = false; vm.archive(h) })
                    DropdownMenuItem({ Text(stringResource(UiR.string.action_delete)) }, { menu = false; confirm = true })
                }
            }
        }
        if (!h.isPaused) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (item.scheduledToday) Text(stringResource(R.string.hb_today, fmt.number(item.today), fmt.number(h.targetPerDay)), style = MaterialTheme.typography.titleMedium)
                    else Text(stringResource(R.string.hb_not_today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.digits(stringResource(R.string.hb_streak, item.stats.currentStreak)) + " · " + fmt.digits(stringResource(R.string.hb_best, item.stats.bestStreak)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.hb_rate, fmt.percent(item.stats.completionRate)), style = MaterialTheme.typography.bodySmall)
                }
                if (item.scheduledToday && h.isSuccess(item.today) && h.kind == HabitKind.BUILD) StatusChip(stringResource(R.string.hb_success), StatusTone.SUCCESS, icon = Icons.Outlined.CheckCircle)
                IconButton(onClick = { vm.increment(h, -1) }, enabled = item.today > 0) { Icon(Icons.Outlined.Remove, stringResource(R.string.hb_remove_one)) }
                FilledIconButton(onClick = { vm.increment(h, 1) }) { Icon(Icons.Outlined.Add, stringResource(R.string.hb_add_one)) }
            }
            Text(stringResource(R.string.hb_heatmap), style = MaterialTheme.typography.labelSmall)
            HeatmapRow(item.heat, stringResource(R.string.hb_rate, fmt.percent(item.stats.completionRate)))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete(h) }, { confirm = false }, destructive = true)
}

@Composable
private fun HabitEditScreen(onBack: () -> Unit, vm: HabitEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    val fmt = LocalFormatter.current
    val ctx = androidx.compose.ui.platform.LocalResources.current
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) R.string.hb_new else R.string.hb_edit), onBack = onBack, snackbarHostState = host,
        actions = { TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) } }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val h = f.habit
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(h.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(UiR.string.label_name), error = f.errors.forField("name"), maxLength = 120)
            FormTextField(h.description, { v -> vm.edit { it.copy(description = v) } }, stringResource(UiR.string.label_description), singleLine = false)
            Text(stringResource(R.string.hb_kind), style = MaterialTheme.typography.titleSmall)
            val kinds = mapOf(HabitKind.BUILD to stringResource(R.string.hb_build), HabitKind.REDUCE to stringResource(R.string.hb_reduce))
            ChoiceChips(HabitKind.entries, h.kind, { kinds.getValue(it) }, { k -> vm.edit { it.copy(kind = k, targetPerDay = if (k == HabitKind.REDUCE) 0 else 1) } })
            NumberField(h.targetPerDay.toString(), { v -> v.toIntOrNull()?.let { t -> vm.edit { it.copy(targetPerDay = t) } } },
                stringResource(if (h.kind == HabitKind.BUILD) R.string.hb_target_build else R.string.hb_target_reduce), error = f.errors.forField("target"))
            Text(stringResource(UiR.string.label_days), style = MaterialTheme.typography.titleSmall)
            WeekdayPicker(h.weekdays, { w -> vm.edit { it.copy(weekdays = w) } }, firstDay)
            if (f.errors.forField("weekdays") != null) Text(stringResource(UiR.string.error_empty_selection), color = MaterialTheme.colorScheme.error)
            Text(stringResource(UiR.string.label_color), style = MaterialTheme.typography.titleSmall)
            ColorSwatchPicker(UserColorChoices, h.color, { c -> vm.edit { it.copy(color = c) } }, { i -> fmt.digits(ctx.getString(UiR.string.label_color_option, i + 1)) })
            TimeField(stringResource(R.string.hb_reminder), h.reminderTime, { t -> vm.edit { it.copy(reminderTime = t) } }, allowClear = true)
            Spacer(Modifier.height(48.dp))
        }
    }
}
