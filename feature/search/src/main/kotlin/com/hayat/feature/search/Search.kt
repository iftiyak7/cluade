package com.hayat.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.domain.repository.SearchRepository
import com.hayat.core.model.SearchResult
import com.hayat.core.model.SearchResultType
import com.hayat.core.navigation.*
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(private val search: SearchRepository) : HayatViewModel() {
    val query = MutableStateFlow("")
    val results = query.debounce(200).mapLatest { q -> if (q.isBlank()) null else runCatching { search.search(q) }.getOrDefault(emptyList()) }.stateInVm(null)
}

fun NavGraphBuilder.searchGraph(nav: NavController) { composable<SearchRoute> { SearchScreen(nav) } }

fun SearchResult.route(): Any = when (type) {
    SearchResultType.TASK -> TaskDetailRoute(id); SearchResultType.ROUTINE -> RoutineDetailRoute(id); SearchResultType.NOTE -> NoteEditRoute(id)
    SearchResultType.JOURNAL -> JournalEditRoute(id); SearchResultType.EVENT -> EventEditRoute(id); SearchResultType.HABIT -> HabitEditRoute(id)
    SearchResultType.REMINDER -> ReminderEditRoute(id); SearchResultType.MEDICINE -> MedicineEditRoute(id); SearchResultType.FINANCE -> FinanceEditRoute(id)
}

@Composable
fun SearchResultType.label(): String = stringResource(when (this) {
    SearchResultType.TASK -> R.string.sr_type_task; SearchResultType.ROUTINE -> R.string.sr_type_routine; SearchResultType.NOTE -> R.string.sr_type_note
    SearchResultType.JOURNAL -> R.string.sr_type_journal; SearchResultType.EVENT -> R.string.sr_type_event; SearchResultType.HABIT -> R.string.sr_type_habit
    SearchResultType.REMINDER -> R.string.sr_type_reminder; SearchResultType.MEDICINE -> R.string.sr_type_medicine; SearchResultType.FINANCE -> R.string.sr_type_finance
})

@Composable
private fun SearchScreen(nav: NavController, vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    HayatScreen(title = stringResource(R.string.sr_title), onBack = nav::popBackStack) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { vm.query.value = it.take(100) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.sr_hint)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            val r = results
            when {
                r == null -> EmptyState(Icons.Outlined.Search, stringResource(R.string.sr_title), stringResource(R.string.sr_start))
                r.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.sr_empty), q)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(r, key = { it.type.name + it.id }) { res ->
                        HayatCard(onClick = { nav.navigate(res.route()) }) {
                            StatusChip(res.type.label(), StatusTone.NEUTRAL)
                            Text(res.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (res.snippet.isNotBlank()) Text(res.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
