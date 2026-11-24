package com.hayat.feature.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.domain.productivity.FinanceCalculator
import com.hayat.core.domain.productivity.FinanceUseCases
import com.hayat.core.domain.repository.FinanceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.FinanceItem
import com.hayat.core.model.FinanceKind
import com.hayat.core.model.FinanceRepeat
import com.hayat.core.navigation.FinanceEditRoute
import com.hayat.core.navigation.FinanceRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.forField
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class FinanceState(val loading: Boolean = true, val items: List<FinanceItem> = emptyList(), val today: LocalDate = LocalDate.now())

@HiltViewModel
class FinanceViewModel @Inject constructor(
    finance: FinanceRepository,
    private val useCases: FinanceUseCases,
    private val time: TimeProvider,
) : HayatViewModel() {
    val state = finance.observeItems().combine(flowOf(time.today())) { items, today -> FinanceState(false, items.filter { !it.isArchived }, today) }.stateInVm(FinanceState())
    fun markPaid(item: FinanceItem, date: LocalDate, amount: Long?) = safeLaunch { useCases.markPaid(item, date, amount).handle { message(R.string.fn_recorded) } }
    fun markUnpaid(item: FinanceItem) = safeLaunch { useCases.markUnpaid(item).handle() }
}

data class FinanceForm(val item: FinanceItem, val isNew: Boolean, val amountText: String, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class FinanceEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val finance: FinanceRepository,
    private val useCases: FinanceUseCases,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<FinanceEditRoute>()
    private val _form = MutableStateFlow<FinanceForm?>(null)
    val form = _form.asStateFlow()
    val payments = (route.id?.let { finance.observePayments(it) } ?: flowOf(emptyList())).stateInVm(emptyList())
    init {
        safeLaunch {
            val now = time.now()
            val currency = prefs.current().currency
            _form.value = route.id?.let { finance.getItem(it) }?.let { FinanceForm(it, false, it.amountMinor?.let(FinanceCalculator::formatMinor).orEmpty()) }
                ?: FinanceForm(FinanceItem(ids.newId(), "", FinanceKind.BILL, currency = currency, dueDate = time.today(), createdAt = now, updatedAt = now), true, "")
        }
    }
    fun edit(t: (FinanceItem) -> FinanceItem) = _form.update { it?.copy(item = t(it.item), errors = emptyList()) }
    fun editAmount(v: String) = _form.update { it?.copy(amountText = v) }
    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val amount = if (f.amountText.isBlank()) null else FinanceCalculator.parseAmount(f.amountText) ?: run { message(UiR.string.error_invalid_format); return@safeLaunch }
        val errors = useCases.save(f.item.copy(amountMinor = amount)).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
    fun delete() = safeLaunch { _form.value?.let { useCases.delete(it.item.id).handle { emit(UiEvent.NavigateBack) } } }
}

fun NavGraphBuilder.financeGraph(nav: NavController) {
    composable<FinanceRoute> { FinanceScreen(nav) }
    composable<FinanceEditRoute> { FinanceEditScreen(nav::popBackStack) }
}

@Composable
fun money(minor: Long?, currency: String): String = minor?.let { LocalFormatter.current.digits(FinanceCalculator.formatMinor(it)) + " " + currency }.orEmpty()

@Composable
private fun FinanceScreen(nav: NavController, vm: FinanceViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var paying by remember { mutableStateOf<FinanceItem?>(null) }
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.fn_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(FinanceEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.fn_new)) }) }) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { InfoBanner(stringResource(R.string.fn_note_offline)) }
            if (s.items.isEmpty()) { item { EmptyState(Icons.Outlined.Payments, stringResource(R.string.fn_title), stringResource(R.string.fn_empty)) }; return@LazyColumn }
            val unpaid = s.items.filter { !it.isPaid }.sortedBy { it.dueDate }
            val monthTotals = unpaid.filter { it.dueDate.month == s.today.month && it.dueDate.year == s.today.year && it.amountMinor != null }.groupBy { it.currency }
            if (monthTotals.isNotEmpty()) item {
                HayatCard { monthTotals.forEach { (cur, l) -> Text(stringResource(R.string.fn_month_total, money(l.sumOf { it.amountMinor ?: 0 }, cur)), style = MaterialTheme.typography.titleSmall) } }
            }
            listOf(R.string.fn_overdue to unpaid.filter { it.dueDate.isBefore(s.today) }, R.string.fn_upcoming to unpaid.filter { !it.dueDate.isBefore(s.today) }, R.string.fn_paid to s.items.filter { it.isPaid })
                .filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                    item { SectionHeader(stringResource(title)) }
                    items(list, key = { it.id }) { f ->
                        HayatCard(onClick = { nav.navigate(FinanceEditRoute(f.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.title, style = MaterialTheme.typography.titleSmall)
                                    Text(listOf(stringResource(f.kind.labelRes()), stringResource(R.string.fn_due_in, fmt.date(f.dueDate)), stringResource(f.repeat.labelRes()), f.counterparty).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(money(f.amountMinor, f.currency), style = MaterialTheme.typography.titleSmall)
                            }
                            if (!f.isPaid) Row {
                                if (f.dueDate.isBefore(s.today)) StatusChip(stringResource(R.string.fn_overdue), StatusTone.ERROR, icon = Icons.Outlined.Warning)
                                Spacer(Modifier.weight(1f))
                                FilledTonalButton(onClick = { paying = f }) { Icon(Icons.Outlined.Check, null); Text(stringResource(R.string.fn_mark_paid)) }
                            } else TextButton(onClick = { vm.markUnpaid(f) }) { Text(stringResource(R.string.fn_mark_unpaid)) }
                        }
                    }
                }
        }
    }
    paying?.let { item ->
        var date by remember { mutableStateOf(s.today) }
        var amount by remember { mutableStateOf(item.amountMinor?.let(FinanceCalculator::formatMinor).orEmpty()) }
        AlertDialog(onDismissRequest = { paying = null }, title = { Text(stringResource(R.string.fn_mark_paid)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(stringResource(R.string.fn_paid_on), date, { d -> d?.let { date = it } })
                    NumberField(amount, { amount = it }, stringResource(R.string.fn_amount), decimal = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.markPaid(item, date, FinanceCalculator.parseAmount(amount)); paying = null }) { Text(stringResource(UiR.string.action_save)) } },
            dismissButton = { TextButton(onClick = { paying = null }) { Text(stringResource(UiR.string.action_cancel)) } })
    }
}

@Composable
private fun FinanceEditScreen(onBack: () -> Unit, vm: FinanceEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    val ctx = LocalContext.current
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) R.string.fn_new else R.string.fn_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val i = f.item
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(i.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), error = f.errors.forField("title"), maxLength = 120)
            Text(stringResource(R.string.fn_kind), style = MaterialTheme.typography.titleSmall)
            val kinds = FinanceKind.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(FinanceKind.entries, i.kind, { kinds.getValue(it) }, { k -> vm.edit { it.copy(kind = k) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(f.amountText, vm::editAmount, stringResource(R.string.fn_amount), Modifier.weight(2f), decimal = true)
                FormTextField(i.currency, { v -> vm.edit { it.copy(currency = v.uppercase().take(3)) } }, stringResource(R.string.fn_currency), Modifier.weight(1f), error = f.errors.forField("currency"))
            }
            DateField(stringResource(R.string.fn_due), i.dueDate, { d -> d?.let { vm.edit { s -> s.copy(dueDate = it) } } })
            Text(stringResource(R.string.fn_repeat), style = MaterialTheme.typography.titleSmall)
            val repeats = FinanceRepeat.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(FinanceRepeat.entries, i.repeat, { repeats.getValue(it) }, { r -> vm.edit { it.copy(repeat = r) } })
            FormTextField(i.counterparty, { v -> vm.edit { it.copy(counterparty = v) } }, stringResource(R.string.fn_counterparty), maxLength = 120)
            Text(stringResource(R.string.fn_lead), style = MaterialTheme.typography.titleSmall)
            val none = stringResource(R.string.fn_lead_none); val same = stringResource(R.string.fn_lead_same)
            ChoiceChips(listOf(null, 0, 1, 3, 7), i.reminderLeadDays, { d -> when (d) { null -> none; 0 -> same; else -> fmt.digits(ctx.getString(R.string.fn_lead_days, d)) } }, { d -> vm.edit { it.copy(reminderLeadDays = d) } })
            FormTextField(i.note, { v -> vm.edit { it.copy(note = v) } }, stringResource(UiR.string.label_notes), singleLine = false)
            if (payments.isNotEmpty()) {
                SectionHeader(stringResource(R.string.fn_history))
                payments.forEach { p -> Text(fmt.date(p.paidOn) + (p.amountMinor?.let { " · " + money(it, i.currency) } ?: ""), style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete() }, { confirm = false }, destructive = true)
}
