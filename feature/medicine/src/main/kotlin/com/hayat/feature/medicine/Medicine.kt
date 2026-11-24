package com.hayat.feature.medicine

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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.health.MedicinePlanner
import com.hayat.core.domain.health.MedicineUseCases
import com.hayat.core.domain.repository.MedicineRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.MealInstruction
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineDose
import com.hayat.core.model.MedicineForm
import com.hayat.core.model.MedicineSchedule
import com.hayat.core.model.WeekdaySet
import com.hayat.core.navigation.MedicineEditRoute
import com.hayat.core.navigation.MedicineRoute
import com.hayat.core.ui.DateField
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

data class MedicineState(val loading: Boolean = true, val today: List<MedicineDose> = emptyList(), val medicines: List<Medicine> = emptyList(), val adherence: Float = 0f, val adherenceTotal: Int = 0)

@HiltViewModel
class MedicineViewModel @Inject constructor(
    medicines: MedicineRepository,
    private val useCases: MedicineUseCases,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(medicines.observeMedicines(), medicines.observeDoseLogs(today.minusDays(6), today)) { meds, logs ->
        val week = (0L..6L).flatMap { MedicinePlanner.dosesFor(today.minusDays(it), meds, logs) }
        MedicineState(false, MedicinePlanner.dosesFor(today, meds, logs), meds, MedicinePlanner.adherence(week), week.size)
    }.stateInVm(MedicineState())

    fun setStatus(dose: MedicineDose, status: DoseStatus?) = safeLaunch { useCases.setDoseStatus(dose, status).handle() }
}

data class MedicineEditForm(val medicine: Medicine, val isNew: Boolean, val stockText: String, val refillText: String, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class MedicineEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val medicines: MedicineRepository,
    private val useCases: MedicineUseCases,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<MedicineEditRoute>()
    private val _form = MutableStateFlow<MedicineEditForm?>(null)
    val form = _form.asStateFlow()
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)

    init {
        safeLaunch {
            val existing = route.id?.let { medicines.getMedicine(it) }
            val now = time.now()
            val m = existing ?: run {
                val id = ids.newId()
                Medicine(id, "", startDate = time.today(), schedules = listOf(MedicineSchedule(ids.newId(), id, LocalTime.of(8, 0))), createdAt = now, updatedAt = now)
            }
            _form.value = MedicineEditForm(m, existing == null, m.stock?.let(::fmtNum).orEmpty(), m.refillThreshold?.let(::fmtNum).orEmpty())
        }
    }

    private fun fmtNum(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    fun edit(transform: (Medicine) -> Medicine) = _form.update { it?.copy(medicine = transform(it.medicine), errors = emptyList()) }
    fun editText(stock: String? = null, refill: String? = null) = _form.update { it?.copy(stockText = stock ?: it.stockText, refillText = refill ?: it.refillText) }
    fun addTime() = edit { m -> m.copy(schedules = m.schedules + MedicineSchedule(ids.newId(), m.id, LocalTime.of(20, 0))) }
    fun editSchedule(id: String, t: (MedicineSchedule) -> MedicineSchedule) = edit { m -> m.copy(schedules = m.schedules.map { if (it.id == id) t(it) else it }) }
    fun removeSchedule(id: String) = edit { m -> m.copy(schedules = m.schedules.filter { it.id != id }) }

    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        if (f.medicine.schedules.isEmpty()) { message(R.string.med_needs_time); return@safeLaunch }
        val med = f.medicine.copy(stock = f.stockText.replace(',', '.').toDoubleOrNull(), refillThreshold = f.refillText.replace(',', '.').toDoubleOrNull())
        val errors = useCases.save(med).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }

    fun archive() = safeLaunch { _form.value?.let { useCases.setArchived(it.medicine.id, !it.medicine.isArchived).handle { emit(UiEvent.NavigateBack) } } }
    fun delete() = safeLaunch { _form.value?.let { useCases.delete(it.medicine.id).handle { emit(UiEvent.NavigateBack) } } }
}

fun NavGraphBuilder.medicineGraph(nav: NavController) {
    composable<MedicineRoute> { MedicineScreen(nav) }
    composable<MedicineEditRoute> { MedicineEditScreen(nav::popBackStack) }
}

fun DoseStatus.tone() = when (this) { DoseStatus.TAKEN -> StatusTone.SUCCESS; DoseStatus.SKIPPED -> StatusTone.NEUTRAL; DoseStatus.MISSED -> StatusTone.ERROR; DoseStatus.POSTPONED -> StatusTone.WARNING }

@Composable
private fun MedicineScreen(nav: NavController, vm: MedicineViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.med_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(MedicineEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.med_new)) }) }) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { InfoBanner(stringResource(R.string.med_disclaimer)) }
            if (s.medicines.isEmpty()) { item { EmptyState(Icons.Outlined.Medication, stringResource(R.string.med_empty_title), stringResource(R.string.med_empty_text)) }; return@LazyColumn }
            item { SectionHeader(stringResource(R.string.med_today)) }
            if (s.today.isEmpty()) item { Text(stringResource(R.string.med_none_today), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.today, key = { it.medicine.id + it.schedule.id }) { d -> DoseCard(d, vm::setStatus) }
            if (s.adherenceTotal > 0) item {
                Text(stringResource(R.string.med_adherence, fmt.percent(s.adherence)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SectionHeader(stringResource(R.string.med_list)) }
            items(s.medicines, key = { it.id }) { m ->
                HayatCard(onClick = { nav.navigate(MedicineEditRoute(m.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Medication, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOf("${m.dose} ${m.unit}".trim(), stringResource(m.form.labelRes()), m.schedules.joinToString(", ") { fmt.time(it.time) })
                                .filter { it.isNotBlank() }.joinToString(" · ").let(fmt::digits), style = MaterialTheme.typography.bodySmall)
                        }
                        if (m.isArchived) StatusChip(stringResource(UiR.string.label_archived), StatusTone.NEUTRAL)
                    }
                    if (m.needsRefill) StatusChip(stringResource(R.string.med_refill_warning, fmt.decimal(m.stock ?: 0.0)), StatusTone.WARNING, icon = Icons.Outlined.Inventory2)
                    if (m.endDate?.isBefore(java.time.LocalDate.now()) == true) StatusChip(stringResource(R.string.med_course_ended), StatusTone.NEUTRAL)
                }
            }
        }
    }
}

@Composable
private fun DoseCard(d: MedicineDose, onStatus: (MedicineDose, DoseStatus?) -> Unit) {
    val fmt = LocalFormatter.current
    HayatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.medicine.name, style = MaterialTheme.typography.titleSmall)
                Text(fmt.digits(stringResource(R.string.med_dose_line, fmt.time(d.schedule.time), "${d.medicine.dose} ${d.medicine.unit}".trim())) +
                    if (d.medicine.mealInstruction != MealInstruction.NONE) " · " + stringResource(d.medicine.mealInstruction.labelRes()) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            d.log?.let { StatusChip(stringResource(it.status.labelRes()), it.status.tone()) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.log == null || d.log?.status == DoseStatus.POSTPONED || d.log?.status == DoseStatus.MISSED) {
                FilledTonalButton(onClick = { onStatus(d, DoseStatus.TAKEN) }) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.med_taken)) }
                OutlinedButton(onClick = { onStatus(d, DoseStatus.SKIPPED) }) { Text(stringResource(R.string.med_skip)) }
                if (d.log == null) TextButton(onClick = { onStatus(d, DoseStatus.POSTPONED) }) { Text(stringResource(R.string.med_postpone)) }
            } else {
                TextButton(onClick = { onStatus(d, null) }) { Icon(Icons.Outlined.Undo, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.med_undo)) }
            }
        }
    }
}

@Composable
private fun MedicineEditScreen(onBack: () -> Unit, vm: MedicineEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) R.string.med_new else R.string.med_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) {
                IconButton(onClick = vm::archive) { Icon(Icons.Outlined.Archive, stringResource(if (f.medicine.isArchived) UiR.string.action_unarchive else UiR.string.action_archive)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val m = f.medicine
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBanner(stringResource(R.string.med_disclaimer))
            FormTextField(m.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(UiR.string.label_name), error = f.errors.forField("name"), maxLength = 120)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(m.dose, { v -> vm.edit { it.copy(dose = v) } }, stringResource(R.string.med_dose), Modifier.weight(1f), maxLength = 40)
                FormTextField(m.unit, { v -> vm.edit { it.copy(unit = v) } }, stringResource(R.string.med_unit), Modifier.weight(1f), maxLength = 40)
            }
            SectionHeader(stringResource(R.string.med_form))
            val forms = MedicineForm.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(MedicineForm.entries, m.form, { forms.getValue(it) }, { v -> vm.edit { it.copy(form = v) } })
            SectionHeader(stringResource(R.string.med_meal))
            val meals = MealInstruction.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(MealInstruction.entries, m.mealInstruction, { meals.getValue(it) }, { v -> vm.edit { it.copy(mealInstruction = v) } })
            SectionHeader(stringResource(R.string.med_times))
            if (f.errors.forField("schedule") != null) Text(stringResource(UiR.string.error_duplicate), color = MaterialTheme.colorScheme.error)
            m.schedules.forEach { sch ->
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(stringResource(UiR.string.label_time), sch.time, { t -> t?.let { vm.editSchedule(sch.id) { s -> s.copy(time = it) } } }, Modifier.weight(1f))
                        NumberField(sch.quantity.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() },
                            { v -> v.replace(',', '.').toDoubleOrNull()?.let { q -> vm.editSchedule(sch.id) { it.copy(quantity = q) } } }, stringResource(R.string.med_quantity), Modifier.weight(1f), decimal = true)
                        IconButton(onClick = { vm.removeSchedule(sch.id) }) { Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    WeekdayPicker(sch.weekdays, { w -> vm.editSchedule(sch.id) { it.copy(weekdays = if (w.isEmpty) WeekdaySet.ALL else w) } }, firstDay)
                }
            }
            TextButton(onClick = vm::addTime) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.med_add_time)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(UiR.string.label_start_date), m.startDate, { d -> d?.let { vm.edit { s -> s.copy(startDate = it) } } }, Modifier.weight(1f))
                DateField(stringResource(UiR.string.label_end_date), m.endDate, { d -> vm.edit { it.copy(endDate = d) } }, Modifier.weight(1f), allowClear = true, isError = f.errors.forField("endDate") != null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(f.stockText, { vm.editText(stock = it) }, stringResource(R.string.med_stock), Modifier.weight(1f), decimal = true, error = f.errors.forField("stock"))
                NumberField(f.refillText, { vm.editText(refill = it) }, stringResource(R.string.med_refill), Modifier.weight(1f), decimal = true, error = f.errors.forField("refillThreshold"))
            }
            SwitchRow(stringResource(R.string.med_reminders), m.reminderEnabled, { v -> vm.edit { it.copy(reminderEnabled = v) } }, icon = Icons.Outlined.NotificationsActive)
            FormTextField(m.notes, { v -> vm.edit { it.copy(notes = v) } }, stringResource(UiR.string.label_notes), singleLine = false, minLines = 2)
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirmDelete = false; vm.delete() }, { confirmDelete = false }, destructive = true)
}
