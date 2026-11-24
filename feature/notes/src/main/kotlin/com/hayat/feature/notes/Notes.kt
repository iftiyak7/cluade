package com.hayat.feature.notes

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
import com.hayat.core.domain.productivity.NoteUseCases
import com.hayat.core.domain.repository.NoteRepository
import com.hayat.core.domain.repository.TagRepository
import com.hayat.core.model.ChecklistItem
import com.hayat.core.model.JournalEntry
import com.hayat.core.model.MoodEntry
import com.hayat.core.model.Note
import com.hayat.core.navigation.JournalEditRoute
import com.hayat.core.navigation.NoteEditRoute
import com.hayat.core.navigation.NotesRoute
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True if the device can verify the user (biometric or screen lock) for locked notes. */
@Composable
fun rememberCanLock(): Boolean {
    val ctx = LocalContext.current
    return remember { BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS }
}

/** Shows the system authentication prompt; [onSuccess] runs only after verification. */
fun authenticate(activity: FragmentActivity?, title: String, onSuccess: () -> Unit) {
    if (activity == null) return
    if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) { onSuccess(); return }
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(AUTHENTICATORS).build())
}

data class NotesState(val loading: Boolean = true, val notes: List<Note> = emptyList(), val journal: List<JournalEntry> = emptyList(), val moods: List<MoodEntry> = emptyList(), val query: String = "", val today: LocalDate = LocalDate.now())

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val today = time.today()
    private val query = MutableStateFlow("")
    val state = combine(notes.observeNotes(), notes.observeJournal(), notes.observeMoods(today.minusDays(13), today), query) { n, j, m, q ->
        val filtered = if (q.isBlank()) n else n.filter { it.title.contains(q, true) || (!it.isLocked && (it.body.contains(q, true) || it.items.any { i -> i.text.contains(q, true) })) || it.tags.any { t -> t.name.contains(q, true) } }
        NotesState(false, filtered.filter { !it.isArchived }, j, m, q, today)
    }.stateInVm(NotesState())
    fun setQuery(q: String) { query.value = q }
    fun toggleItem(item: ChecklistItem) = safeLaunch { notes.setChecklistItem(item.id, !item.isChecked, time.now()) }
    fun logMood(score: Int) = safeLaunch { notes.saveMood(MoodEntry(ids.newId(), today, time.now(), score)); message(R.string.md_saved) }
    fun deleteMood(m: MoodEntry) = safeLaunch { notes.deleteMood(m.id) }
}

data class NoteForm(val note: Note, val isNew: Boolean, val tagsText: String, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class NoteEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val notes: NoteRepository,
    private val tags: TagRepository,
    private val useCases: NoteUseCases,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<NoteEditRoute>()
    private val _form = MutableStateFlow<NoteForm?>(null)
    val form = _form.asStateFlow()
    init {
        safeLaunch {
            val now = time.now()
            _form.value = route.id?.let { notes.getNote(it) }?.let { NoteForm(it, false, it.tags.joinToString(", ") { t -> t.name }) }
                ?: NoteForm(Note(ids.newId(), "", isChecklist = route.checklist, items = if (route.checklist) listOf(ChecklistItem(ids.newId(), "", false, 0)) else emptyList(), createdAt = now, updatedAt = now), true, "")
        }
    }
    fun edit(t: (Note) -> Note) = _form.update { it?.copy(note = t(it.note), errors = emptyList()) }
    fun editTags(v: String) = _form.update { it?.copy(tagsText = v) }
    fun addItem() = edit { n -> n.copy(items = n.items + ChecklistItem(ids.newId(), "", false, n.items.size)) }
    fun editItem(id: String, t: (ChecklistItem) -> ChecklistItem) = edit { n -> n.copy(items = n.items.map { if (it.id == id) t(it) else it }) }
    fun removeItem(id: String) = edit { n -> n.copy(items = n.items.filter { it.id != id }) }
    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val tagList = f.tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(10).map { tags.getOrCreate(it) }
        val errors = useCases.save(f.note.copy(items = f.note.items.filter { it.text.isNotBlank() }, tags = tagList)).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
    fun delete() = safeLaunch { _form.value?.let { useCases.delete(it.note.id).handle { emit(UiEvent.NavigateBack) } } }
}

@HiltViewModel
class JournalEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val notes: NoteRepository,
    private val useCases: NoteUseCases,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<JournalEditRoute>()
    private val _form = MutableStateFlow<JournalEntry?>(null)
    val form = _form.asStateFlow()
    val isNew get() = route.id == null
    init {
        safeLaunch {
            val now = time.now()
            _form.value = route.id?.let { notes.getJournal(it) } ?: JournalEntry(ids.newId(), time.today(), "", "", createdAt = now, updatedAt = now)
        }
    }
    fun edit(t: (JournalEntry) -> JournalEntry) = _form.update { it?.let(t) }
    fun save() = safeLaunch { _form.value?.let { useCases.saveJournal(it).handle { emit(UiEvent.NavigateBack) } } }
    fun delete() = safeLaunch { _form.value?.let { notes.deleteJournal(it.id, time.now()); emit(UiEvent.NavigateBack) } }
}

fun NavGraphBuilder.notesGraph(nav: NavController) {
    composable<NotesRoute> { NotesScreen(nav) }
    composable<NoteEditRoute> { NoteEditScreen(nav::popBackStack) }
    composable<JournalEditRoute> { JournalEditScreen(nav::popBackStack) }
}

@Composable fun moodLabel(score: Int) = stringResource(when (score) { 1 -> R.string.md_1; 2 -> R.string.md_2; 3 -> R.string.md_3; 4 -> R.string.md_4; else -> R.string.md_5 })
fun moodEmoji(score: Int) = when (score) { 1 -> "😞"; 2 -> "🙁"; 3 -> "😐"; 4 -> "🙂"; else -> "😄" }

@Composable
private fun NotesScreen(nav: NavController, vm: NotesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val activity = LocalContext.current as? FragmentActivity
    val unlockTitle = stringResource(R.string.nt_unlock_title)
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.nt_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = {
            when (tab) {
                0 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExtendedFloatingActionButton(onClick = { nav.navigate(NoteEditRoute(checklist = true)) }, icon = { Icon(Icons.Outlined.Checklist, null) }, text = { Text(stringResource(R.string.nt_new_checklist)) })
                    ExtendedFloatingActionButton(onClick = { nav.navigate(NoteEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.nt_new_note)) })
                }
                1 -> ExtendedFloatingActionButton(onClick = { nav.navigate(JournalEditRoute()) }, icon = { Icon(Icons.Outlined.Book, null) }, text = { Text(stringResource(R.string.jr_new)) })
            }
        }) { pad ->
        Column(Modifier.padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.nt_tab_notes, R.string.nt_tab_journal, R.string.nt_tab_mood).forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(stringResource(t)) }) }
            }
            if (s.loading) { LoadingBox(); return@Column }
            LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        item { OutlinedTextField(s.query, vm::setQuery, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.nt_search)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true) }
                        if (s.notes.isEmpty()) item { EmptyState(Icons.Outlined.StickyNote2, stringResource(R.string.nt_tab_notes), stringResource(R.string.nt_empty)) }
                        items(s.notes, key = { it.id }) { n ->
                            HayatCard(onClick = { if (n.isLocked) authenticate(activity, unlockTitle) { nav.navigate(NoteEditRoute(n.id)) } else nav.navigate(NoteEditRoute(n.id)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(n.title.ifBlank { n.body.take(40) }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (n.isPinned) Icon(Icons.Outlined.PushPin, stringResource(R.string.nt_pin), tint = MaterialTheme.colorScheme.primary)
                                    if (n.isLocked) Icon(Icons.Outlined.Lock, stringResource(R.string.nt_locked))
                                }
                                if (n.isLocked) StatusChip(stringResource(R.string.nt_locked), StatusTone.NEUTRAL, icon = Icons.Outlined.Lock)
                                else if (n.isChecklist) n.items.take(5).forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(item.isChecked, { vm.toggleItem(item) })
                                        Text(item.text, textDecoration = if (item.isChecked) TextDecoration.LineThrough else null)
                                    }
                                } else Text(n.body, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                if (n.tags.isNotEmpty()) Text(n.tags.joinToString("  ") { "#" + it.name }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    1 -> {
                        if (s.journal.isEmpty()) item { EmptyState(Icons.Outlined.Book, stringResource(R.string.nt_tab_journal), stringResource(R.string.jr_empty)) }
                        items(s.journal, key = { it.id }) { j ->
                            HayatCard(onClick = { if (j.isLocked) authenticate(activity, unlockTitle) { nav.navigate(JournalEditRoute(j.id)) } else nav.navigate(JournalEditRoute(j.id)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(fmt.date(j.date) + (j.mood?.let { "  " + moodEmoji(it) } ?: ""), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    if (j.isLocked) Icon(Icons.Outlined.Lock, stringResource(R.string.nt_locked))
                                }
                                if (j.title.isNotBlank()) Text(j.title, style = MaterialTheme.typography.titleSmall)
                                if (!j.isLocked) Text(j.body, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    2 -> {
                        item {
                            HayatCard {
                                Text(stringResource(R.string.md_today), style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    (1..5).forEach { score ->
                                        val label = moodLabel(score)
                                        TextButton(onClick = { vm.logMood(score) }) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(moodEmoji(score), style = MaterialTheme.typography.headlineMedium)
                                                Text(label, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { SectionHeader(stringResource(R.string.md_history)) }
                        items(s.moods, key = { it.id }) { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(moodEmoji(m.score), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Text(fmt.dayMonth(m.date) + " · " + moodLabel(m.score), Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteMood(m) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditScreen(onBack: () -> Unit, vm: NoteEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val canLock = rememberCanLock()
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) (if (f?.note?.isChecklist == true) R.string.nt_new_checklist else R.string.nt_new_note) else R.string.nt_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null) {
                IconButton(onClick = { vm.edit { it.copy(isPinned = !it.isPinned) } }) { Icon(if (f.note.isPinned) Icons.Outlined.PushPin else Icons.Outlined.PushPin, stringResource(if (f.note.isPinned) R.string.nt_unpin else R.string.nt_pin), tint = if (f.note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!f.isNew) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val n = f.note
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(n.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), error = f.errors.forField("title"), maxLength = 120)
            if (n.isChecklist) {
                n.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.isChecked, { c -> vm.editItem(item.id) { it.copy(isChecked = c) } })
                        OutlinedTextField(item.text, { v -> vm.editItem(item.id) { it.copy(text = v.take(200)) } }, Modifier.weight(1f), singleLine = true)
                        IconButton(onClick = { vm.removeItem(item.id) }) { Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) }
                    }
                }
                TextButton(onClick = vm::addItem) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.nt_add_item)) }
            } else {
                OutlinedTextField(n.body, { v -> vm.edit { it.copy(body = v.take(50_000)) } }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.nt_body)) }, minLines = 8)
            }
            FormTextField(f.tagsText, vm::editTags, stringResource(R.string.nt_tags), supporting = stringResource(R.string.nt_tags_hint))
            SwitchRow(stringResource(R.string.nt_lock), n.isLocked && canLock, { v -> vm.edit { it.copy(isLocked = v) } },
                subtitle = stringResource(if (canLock) R.string.nt_lock_sub else R.string.nt_lock_unavailable), icon = Icons.Outlined.Lock, enabled = canLock)
            Text(stringResource(R.string.nt_reminder), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(UiR.string.label_date), n.reminderAt?.toLocalDate(), { d -> vm.edit { it.copy(reminderAt = d?.let { day -> LocalDateTime.of(day, it.reminderAt?.toLocalTime() ?: LocalTime.of(9, 0)) }) } }, Modifier.weight(1f), allowClear = true)
                TimeField(stringResource(UiR.string.label_time), n.reminderAt?.toLocalTime(), { t -> vm.edit { it.copy(reminderAt = it.reminderAt?.let { r -> t?.let { r.toLocalDate().atTime(t) } }) } }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete() }, { confirm = false }, destructive = true)
}

@Composable
private fun JournalEditScreen(onBack: () -> Unit, vm: JournalEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val canLock = rememberCanLock()
    val j = form
    HayatScreen(title = stringResource(if (vm.isNew) R.string.jr_new else R.string.jr_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (!vm.isNew) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (j == null) { LoadingBox(); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(stringResource(UiR.string.label_date), j.date, { d -> d?.let { vm.edit { e -> e.copy(date = it) } } })
            FormTextField(j.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), maxLength = 120)
            OutlinedTextField(j.body, { v -> vm.edit { it.copy(body = v.take(50_000)) } }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.nt_body)) }, minLines = 10)
            Text(stringResource(R.string.jr_mood), style = MaterialTheme.typography.titleSmall)
            val labels = (1..5).associateWith { moodEmoji(it) + " " + moodLabel(it) }
            ChoiceChips((1..5).toList(), j.mood, { labels.getValue(it) }, { m -> vm.edit { it.copy(mood = if (it.mood == m) null else m) } })
            SwitchRow(stringResource(R.string.nt_lock), j.isLocked && canLock, { v -> vm.edit { it.copy(isLocked = v) } },
                subtitle = stringResource(if (canLock) R.string.nt_lock_sub else R.string.nt_lock_unavailable), icon = Icons.Outlined.Lock, enabled = canLock)
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete() }, { confirm = false }, destructive = true)
}

