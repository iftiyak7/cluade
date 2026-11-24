package com.hayat.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.InkTag
import com.hayat.core.designsystem.component.animateEnter
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.domain.announcement.AnnouncementPolicy
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.Announcement
import com.hayat.core.navigation.*
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import java.time.ZoneId
import javax.inject.Inject

/** Maps an announcement link to an in-app route or opens an https link in the browser. */
internal fun openAnnouncementLink(url: String, nav: NavController, uri: UriHandler) {
    if (!AnnouncementPolicy.isSafeLink(url)) return
    val route: Any? = when (url) {
        "hayat://home" -> HomeRoute
        "hayat://calendar" -> CalendarRoute()
        "hayat://tasks" -> TasksRoute
        "hayat://reminders" -> RemindersRoute
        "hayat://water" -> WaterRoute
        "hayat://medicine" -> MedicineRoute
        "hayat://habits" -> HabitsRoute
        "hayat://ibadah" -> IbadahRoute
        "hayat://notes" -> NotesRoute
        "hayat://progress" -> ProgressRoute
        "hayat://settings" -> SettingsRoute
        else -> null
    }
    if (route != null) nav.navigate(route) { launchSingleTop = true }
    else runCatching { uri.openUri(url) } // https only (checked above); no browser → ignore
}

/** Home-screen banner: brand gradient, plain text only, optional button, dismissible. */
@Composable
internal fun AnnouncementBanner(a: Announcement, bengali: Boolean, modifier: Modifier = Modifier, onCta: () -> Unit, onDismiss: () -> Unit) {
    val ext = HayatThemeExt.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(ext.gradientStart, ext.gradientEnd)))
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkTag(stringResource(R.string.db_news_tag))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.db_news_dismiss), tint = Color.White) }
        }
        Column(Modifier.padding(end = 12.dp)) {
            Text(a.title(bengali), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(a.body(bengali), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
            val label = a.ctaLabel(bengali)
            if (a.ctaUrl.isNotBlank() && label.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onCta, colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = ext.ink)) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

data class AnnouncementsState(val loading: Boolean = true, val enabled: Boolean = true, val items: List<Announcement> = emptyList(), val bengali: Boolean = false)

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repo: AnnouncementRepository,
    prefs: PreferencesRepository,
    config: AppConfig,
    time: TimeProvider,
) : HayatViewModel() {
    val state = combine(repo.observeAll(), prefs.preferences) { all, p ->
        val bengali = AnnouncementPolicy.isBengali(p.language, config.systemLanguage)
        AnnouncementsState(false, p.announcementsEnabled, if (p.announcementsEnabled) AnnouncementPolicy.visible(all, time.now(), bengali, config.versionCode) else emptyList(), bengali)
    }.stateInVm(AnnouncementsState())

    fun markSeen(ids: List<Long>) = safeLaunch { repo.markSeen(ids) }
    fun dismiss(id: Long) = safeLaunch { repo.dismiss(id) }
}

@Composable
internal fun AnnouncementsScreen(nav: NavController, vm: AnnouncementsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    LaunchedEffect(s.items) { s.items.filter { !it.seen }.map { it.id }.takeIf { it.isNotEmpty() }?.let(vm::markSeen) }
    HayatScreen(title = stringResource(R.string.db_news), onBack = nav::popBackStack) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!s.enabled) item {
                EmptyState(Icons.Outlined.NotificationsOff, stringResource(R.string.db_news_off_title), stringResource(R.string.db_news_off_text)) {
                    FilledTonalButton(onClick = { nav.navigate(SettingsRoute) }) { Text(stringResource(R.string.db_news_open_settings)) }
                }
            } else if (s.items.isEmpty()) item {
                EmptyState(Icons.Outlined.Campaign, stringResource(R.string.db_news_empty_title), stringResource(R.string.db_news_empty_text))
            }
            itemsIndexed(s.items, key = { _, a -> a.id }) { i, a ->
                HayatCard(Modifier.animateEnter(i)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.dayMonth(a.startsAt.atZone(ZoneId.systemDefault()).toLocalDate()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    Text(a.title(s.bengali), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(a.body(s.bengali), style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))
                    val label = a.ctaLabel(s.bengali)
                    if (a.ctaUrl.isNotBlank() && label.isNotBlank()) {
                        FilledTonalButton(onClick = { openAnnouncementLink(a.ctaUrl, nav, uri) }, modifier = Modifier.padding(top = 10.dp)) { Text(label) }
                    }
                }
            }
            if (s.enabled) item { InfoBanner(stringResource(R.string.db_news_privacy)) }
        }
    }
}

