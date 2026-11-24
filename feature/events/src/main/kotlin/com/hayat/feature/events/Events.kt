package com.hayat.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.productivity.EventCalculator
import com.hayat.core.domain.productivity.EventUseCases
import com.hayat.core.domain.repository.EventRepository
import com.hayat.core.model.CalendarEvent
import com.hayat.core.model.EventKind
import com.hayat.core.navigation.EventEditRoute
import com.hayat.core.navigation.EventsRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.forField
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class EventRow(val event: CalendarEvent, val next: LocalDate?, val daysUntil: Long?)
data class EventsState(val loading: Boolean = true, val upcoming: List<EventRow> = emptyList(), val past: List<EventRow> = emptyList(), val today: LocalDate = LocalDate.now())

@HiltViewModel
class EventsViewModel @Inject constructor(events: EventRepository, private val useCases: EventUseCases, private val time: TimeProvider) : HayatViewModel() {
    val state = events.observeEvents().map { list ->
        val today = time.today()
        val rows = list.filter { !it.isArchived }.map { e -> EventCalculator.nextOccurrence(e, today).let { n -> EventRow(e, n, n?.let { EventCalculator.daysUntil(e, today) }) } }
        EventsState(false, rows.filter { it.next != null && !it.event.isCompleted }.sortedBy { it.next }, rows.filter { it.next == null || it.event.isCompleted }.sortedByDescending { it.event.date }, today)
    }.stateInVm(EventsState())
    fun toggleCompleted(e: CalendarEvent) = safeLaunch { useCases.save(e.copy(isCompleted = !e.isCompleted)).handle() }
}

data class EventForm(val event: CalendarEvent, val isNew: Boolean, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class EventEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val eventRepo: EventRepository,
    private val useCases: EventUseCases,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<EventEditRoute>()
    private val _form = MutableStateFlow<EventForm?>(null)
    val form = _form.asStateFlow()
    init {
        safeLaunch {
            val now = time.now()
            _form.value = route.id?.let { eventRepo.getEvent(it) }?.let { EventForm(it, false) }
                ?: EventForm(CalendarEvent(ids.newId(), "", EventKind.BIRTHDAY, date = route.date?.let(LocalDate::parse) ?: time.today(), repeatsAnnually = true, reminderLeadDays = listOf(1, 0), createdAt = now, updatedAt = now), true)
        }
    }
    fun edit(t: (CalendarEvent) -> CalendarEvent) = _form.update { it?.copy(event = t(it.event), errors = emptyList()) }
    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val errors = useCases.save(f.event).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
    fun delete() = safeLaunch { _form.value?.let { useCases.delete(it.event.id).handle { emit(UiEvent.NavigateBack) } } }
}

fun NavGraphBuilder.eventsGraph(nav: NavController) {
    composable<EventsRoute> { EventsScreen(nav) }
    composable<EventEditRoute> { EventEditScreen(nav::popBackStack) }
}

fun EventKind.icon() = when (this) {
    EventKind.BIRTHDAY -> Icons.Outlined.Cake; EventKind.ANNIVERSARY -> Icons.Outlined.Favorite; EventKind.APPOINTMENT -> Icons.Outlined.MedicalServices
    EventKind.TRAVEL -> Icons.Outlined.Flight; EventKind.DOCUMENT_EXPIRY -> Icons.Outlined.Description; EventKind.LICENSE_RENEWAL -> Icons.Outlined.Badge
    EventKind.CUSTOM -> Icons.Outlined.Event
}

@Composable
private fun EventsScreen(nav: NavController, vm: EventsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.ev_title), onBack = nav::popBackStack,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(EventEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.ev_new)) }) }) { pad ->
        when {
            s.loading -> LoadingBox(Modifier.padding(pad))
            s.upcoming.isEmpty() && s.past.isEmpty() -> EmptyState(Icons.Outlined.Cake, stringResource(R.string.ev_empty_title), stringResource(R.string.ev_empty_text), Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(R.string.ev_upcoming to s.upcoming, R.string.ev_past to s.past).filter { it.second.isNotEmpty() }.forEach { (title, rows) ->
                    item { SectionHeader(stringResource(title)) }
                    items(rows, key = { it.event.id }) { r ->
                        val e = r.event
                        HayatCard(onClick = { nav.navigate(EventEditRoute(e.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(e.kind.icon(), null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(e.title, style = MaterialTheme.typography.titleSmall)
                                    val years = r.next?.let { EventCalculator.yearsAt(e, it) }
                                    Text(listOfNotNull(e.customLabel.ifBlank { null } ?: stringResource(e.kind.labelRes()), r.next?.let(fmt::date) ?: fmt.date(e.date),
                                        years?.let { stringResource(if (e.kind == EventKind.BIRTHDAY) R.string.ev_turns else R.string.ev_years, it) }?.let(fmt::digits)).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                r.daysUntil?.takeIf { !e.isCompleted }?.let { d ->
                                    StatusChip(if (d == 0L) stringResource(R.string.ev_today) else fmt.digits(stringResource(R.string.ev_in_days, d.toInt())), if (d <= 3) StatusTone.WARNING else StatusTone.INFO)
                                }
                            }
                            if (!e.repeatsAnnually) TextButton(onClick = { vm.toggleCompleted(e) }) { Text(stringResource(if (e.isCompleted) R.string.ev_mark_open else R.string.ev_mark_done)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EventEditScreen(onBack: () -> Unit, vm: EventEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    val ctx = LocalContext.current
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) R.string.ev_new else R.string.ev_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val e = f.event
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(e.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), error = f.errors.forField("title"), maxLength = 120)
            Text(stringResource(R.string.ev_kind), style = MaterialTheme.typography.titleSmall)
            val kinds = EventKind.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(EventKind.entries, e.kind, { kinds.getValue(it) }, { k ->
                vm.edit { it.copy(kind = k, repeatsAnnually = if (f.isNew) k == EventKind.BIRTHDAY || k == EventKind.ANNIVERSARY else it.repeatsAnnually) }
            })
            if (e.kind == EventKind.CUSTOM) FormTextField(e.customLabel, { v -> vm.edit { it.copy(customLabel = v) } }, stringResource(R.string.ev_label), maxLength = 40)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(UiR.string.label_date), e.date, { d -> d?.let { vm.edit { s -> s.copy(date = it) } } }, Modifier.weight(1f))
                TimeField(stringResource(UiR.string.label_time), e.time, { t -> vm.edit { it.copy(time = t) } }, Modifier.weight(1f), allowClear = true)
            }
            SwitchRow(stringResource(R.string.ev_repeat_yearly), e.repeatsAnnually, { v -> vm.edit { it.copy(repeatsAnnually = v) } })
            Text(stringResource(R.string.ev_reminders), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 3, 7, 14, 30).forEach { d ->
                    val on = d in e.reminderLeadDays
                    FilterChip(on, { vm.edit { it.copy(reminderLeadDays = if (on) it.reminderLeadDays - d else it.reminderLeadDays + d) } },
                        label = { Text(if (d == 0) stringResource(R.string.ev_on_day) else fmt.digits(ctx.getString(R.string.ev_days_before, d))) })
                }
            }
            FormTextField(e.notes, { v -> vm.edit { it.copy(notes = v) } }, stringResource(UiR.string.label_notes), singleLine = false, minLines = 2)
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete() }, { confirm = false }, destructive = true)
}
