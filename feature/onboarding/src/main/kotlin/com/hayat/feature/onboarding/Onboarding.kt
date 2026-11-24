package com.hayat.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.designsystem.theme.UserColorChoices
import com.hayat.core.domain.reminder.ReminderCapabilityChecker
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.routine.RoutineActionsUseCase
import com.hayat.core.domain.template.BuiltInTemplates
import com.hayat.core.model.AppLanguage
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.model.ThemeMode
import com.hayat.core.model.UserPreferences
import com.hayat.core.navigation.HomeRoute
import com.hayat.core.navigation.OnboardingRoute
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.TemplateStrings
import com.hayat.core.ui.TimeField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val routines: RoutineActionsUseCase,
    private val capabilities: ReminderCapabilityChecker,
    private val time: TimeProvider,
) : HayatViewModel() {
    val preferences = prefs.preferences.stateInVm(null)

    fun update(transform: (UserPreferences) -> UserPreferences) = safeLaunch { prefs.update(transform) }

    /** Completes onboarding; optionally creates a routine from a template with localized titles. */
    fun finish(template: RoutineTemplate?, name: String?, itemTitles: List<String>, onDone: () -> Unit) = safeLaunch {
        if (template != null && name != null) {
            val localized = template.copy(items = template.items.mapIndexed { i, item -> item.copy(title = itemTitles.getOrElse(i) { item.title }) })
            routines.applyTemplate(localized, name, time.today(), UserColorChoices.first())
        }
        prefs.update { it.copy(onboardingCompleted = true) }
        capabilities.requestReconcile()
        onDone()
    }
}

fun NavGraphBuilder.onboardingGraph(nav: NavController) {
    composable<OnboardingRoute> {
        OnboardingScreen(onDone = { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } })
    }
}

private const val STEPS = 6

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val res = androidx.compose.ui.platform.LocalResources.current
    val p = prefs ?: return LoadingBox()

    val template = templateId?.let(BuiltInTemplates::byId)
    val templateName = template?.systemKey?.let(TemplateStrings::name)?.let { stringResource(it) }
    val itemTitles = template?.items?.map { item -> TemplateStrings.item(item.title)?.let { res.getString(it) } ?: item.title }.orEmpty()
    val finish = { vm.finish(template, templateName, itemTitles, onDone) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ob_step, step + 1, STEPS), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.finish(null, null, emptyList(), onDone) }) { Text(stringResource(R.string.ob_skip)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            when (step) {
                0 -> {
                    Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
                    Text(stringResource(R.string.ob_welcome_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.ob_welcome_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.ob_language), style = MaterialTheme.typography.titleMedium)
                    val labels = mapOf(AppLanguage.SYSTEM to stringResource(R.string.ob_auto), AppLanguage.ENGLISH to "English", AppLanguage.BENGALI to "বাংলা")
                    ChoiceChips(AppLanguage.entries, p.language, { labels.getValue(it) }, { l -> vm.update { it.copy(language = l) } })
                }
                1 -> {
                    Text(stringResource(R.string.ob_theme), style = MaterialTheme.typography.headlineSmall)
                    val labels = mapOf(ThemeMode.SYSTEM to stringResource(R.string.ob_auto), ThemeMode.LIGHT to stringResource(R.string.ob_light), ThemeMode.DARK to stringResource(R.string.ob_dark))
                    ChoiceChips(ThemeMode.entries, p.theme, { labels.getValue(it) }, { t -> vm.update { it.copy(theme = t) } })
                }
                2 -> {
                    Text(stringResource(R.string.ob_day_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.ob_day_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TimeField(stringResource(R.string.ob_wake), p.wakeTime, { t -> t?.let { vm.update { s -> s.copy(wakeTime = it) } } })
                    TimeField(stringResource(R.string.ob_sleep), p.sleepTime, { t -> t?.let { vm.update { s -> s.copy(sleepTime = it) } } })
                }
                3 -> {
                    Text(stringResource(R.string.ob_categories_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.ob_categories_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow(stringResource(R.string.ob_islamic), p.islamicModuleEnabled, { v -> vm.update { it.copy(islamicModuleEnabled = v) } })
                    SwitchRow(stringResource(R.string.ob_water), p.water.reminderEnabled, { v -> vm.update { it.copy(water = it.water.copy(reminderEnabled = v)) } })
                    SwitchRow(stringResource(R.string.ob_bedtime), p.bedtimeReminderEnabled, { v -> vm.update { it.copy(bedtimeReminderEnabled = v) } })
                }
                4 -> NotificationStep()
                5 -> {
                    Text(stringResource(R.string.ob_template_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.ob_template_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val options = listOf<RoutineTemplate?>(null) + BuiltInTemplates.all.filter { p.islamicModuleEnabled || (it.systemKey != "muslim" && it.systemKey != "ramadan") }
                    options.forEach { t ->
                        val selected = templateId == t?.id
                        Row(
                            Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { templateId = t?.id }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(t?.systemKey?.let(TemplateStrings::name)?.let { stringResource(it) } ?: stringResource(R.string.ob_no_template), style = MaterialTheme.typography.bodyLarge)
                                t?.systemKey?.let(TemplateStrings::description)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }, Modifier.weight(1f)) { Text(stringResource(com.hayat.core.ui.R.string.action_back)) }
            Button(onClick = { if (step < STEPS - 1) step++ else finish() }, Modifier.weight(1f)) {
                Text(stringResource(if (step < STEPS - 1) R.string.ob_next else R.string.ob_finish))
            }
        }
    }
}

@Composable
private fun NotificationStep() {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var isGranted by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted = it }
    Text(stringResource(R.string.ob_notifications_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.ob_notifications_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (isGranted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.ob_notifications_granted), Modifier.padding(start = 8.dp))
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.ob_allow_notifications)) }
    }
}
