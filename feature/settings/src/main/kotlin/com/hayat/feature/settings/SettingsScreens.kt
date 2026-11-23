package com.hayat.feature.settings

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.SettingsRow
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.reminder.SystemSettingsTarget
import com.hayat.core.domain.security.PinCheck
import com.hayat.core.model.*
import com.hayat.core.navigation.*
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.labelRes
import com.hayat.core.ui.R as UiR

fun NavGraphBuilder.settingsGraph(nav: NavController, appVersion: String) {
    composable<SettingsRoute> { SettingsHomeScreen(nav) }
    composable<GeneralSettingsRoute> { GeneralSettingsScreen(nav::popBackStack) }
    composable<AppearanceSettingsRoute> { AppearanceSettingsScreen(nav::popBackStack) }
    composable<NotificationSettingsRoute> { ReminderSettingsScreen(nav::popBackStack) }
    composable<LifestyleSettingsRoute> { LifestyleSettingsScreen(nav::popBackStack) { nav.navigate(PrayerSettingsRoute) } }
    composable<PrivacySettingsRoute> { PrivacySettingsScreen(nav::popBackStack) }
    composable<DashboardSettingsRoute> { DashboardSettingsScreen(nav::popBackStack) }
    composable<AboutRoute> { AboutScreen(nav::popBackStack, appVersion) }
}

@Composable
private fun SettingsHomeScreen(nav: NavController) {
    HayatScreen(title = stringResource(UiR.string.feature_settings), onBack = null) { pad ->
        LazyColumn(contentPadding = pad) {
            item { SettingsRow(stringResource(R.string.settings_general), subtitle = stringResource(R.string.settings_general_sub), icon = Icons.Outlined.Language, onClick = { nav.navigate(GeneralSettingsRoute) }) }
            item { SettingsRow(stringResource(R.string.settings_appearance), subtitle = stringResource(R.string.settings_appearance_sub), icon = Icons.Outlined.Palette, onClick = { nav.navigate(AppearanceSettingsRoute) }) }
            item { SettingsRow(stringResource(R.string.settings_reminders), subtitle = stringResource(R.string.settings_reminders_sub), icon = Icons.Outlined.NotificationsActive, onClick = { nav.navigate(NotificationSettingsRoute) }) }
            item { SettingsRow(stringResource(R.string.settings_lifestyle), subtitle = stringResource(R.string.settings_lifestyle_sub), icon = Icons.Outlined.Spa, onClick = { nav.navigate(LifestyleSettingsRoute) }) }
            item { SettingsRow(stringResource(R.string.settings_dashboard), subtitle = stringResource(R.string.settings_dashboard_sub), icon = Icons.Outlined.Dashboard, onClick = { nav.navigate(DashboardSettingsRoute) }) }
            item { SettingsRow(stringResource(R.string.settings_privacy), subtitle = stringResource(R.string.settings_privacy_sub), icon = Icons.Outlined.Lock, onClick = { nav.navigate(PrivacySettingsRoute) }) }
            item { SettingsRow(stringResource(UiR.string.feature_backup), subtitle = stringResource(R.string.settings_backup_sub), icon = Icons.Outlined.Backup, onClick = { nav.navigate(BackupRoute) }) }
            item { SettingsRow(stringResource(UiR.string.feature_templates), icon = Icons.Outlined.ViewAgenda, onClick = { nav.navigate(TemplatesRoute) }) }
            item { SettingsRow(stringResource(R.string.settings_about), icon = Icons.Outlined.Info, onClick = { nav.navigate(AboutRoute) }) }
        }
    }
}

/** Shared frame for preference screens: waits for preferences, shows snackbar messages. */
@Composable
private fun PrefsScreen(title: String, onBack: () -> Unit, content: LazyListScope.(UserPreferences, SettingsViewModel) -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    HayatScreen(title = title, onBack = onBack, snackbarHostState = host) { pad ->
        val p = prefs
        if (p == null) LoadingBox() else LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) { content(p, vm) }
    }
}

private fun LazyListScope.chips(title: Int, content: @Composable () -> Unit) {
    item {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun GeneralSettingsScreen(onBack: () -> Unit) = PrefsScreen(stringResource(R.string.settings_general), onBack) { p, vm ->
    chips(R.string.pref_language) {
        val labels = mapOf(AppLanguage.SYSTEM to stringResource(R.string.lang_system), AppLanguage.ENGLISH to stringResource(R.string.lang_english), AppLanguage.BENGALI to stringResource(R.string.lang_bengali))
        ChoiceChips(AppLanguage.entries, p.language, { labels.getValue(it) }, { l -> vm.update { it.copy(language = l) } })
    }
    chips(R.string.pref_first_day) {
        val fmt = LocalFormatter.current
        ChoiceChips(listOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY), p.firstDayOfWeek, { fmt.weekdayFull(it) }, { d -> vm.update { it.copy(firstDayOfWeek = d) } })
    }
    chips(R.string.pref_time_format) {
        val labels = mapOf(TimeFormatPref.SYSTEM to stringResource(R.string.time_system), TimeFormatPref.H12 to stringResource(R.string.time_12), TimeFormatPref.H24 to stringResource(R.string.time_24))
        ChoiceChips(TimeFormatPref.entries, p.timeFormat, { labels.getValue(it) }, { t -> vm.update { it.copy(timeFormat = t) } })
    }
    chips(R.string.pref_date_format) {
        val labels = mapOf(DateFormatPref.SHORT to stringResource(R.string.date_short), DateFormatPref.MEDIUM to stringResource(R.string.date_medium), DateFormatPref.LONG to stringResource(R.string.date_long))
        ChoiceChips(DateFormatPref.entries, p.dateFormat, { labels.getValue(it) }, { d -> vm.update { it.copy(dateFormat = d) } })
    }
    chips(R.string.pref_digits) {
        val labels = mapOf(DigitStyle.AUTO to stringResource(R.string.digits_auto), DigitStyle.LATIN to stringResource(R.string.digits_latin), DigitStyle.BENGALI to stringResource(R.string.digits_bengali))
        ChoiceChips(DigitStyle.entries, p.digitStyle, { labels.getValue(it) }, { d -> vm.update { it.copy(digitStyle = d) } })
    }
    item { SwitchRow(stringResource(R.string.pref_overdue), p.showOverdueInToday, { v -> vm.update { it.copy(showOverdueInToday = v) } }, subtitle = stringResource(R.string.pref_overdue_sub)) }
}

@Composable
private fun AppearanceSettingsScreen(onBack: () -> Unit) = PrefsScreen(stringResource(R.string.settings_appearance), onBack) { p, vm ->
    chips(R.string.pref_theme) {
        val labels = mapOf(ThemeMode.SYSTEM to stringResource(R.string.theme_system), ThemeMode.LIGHT to stringResource(R.string.theme_light), ThemeMode.DARK to stringResource(R.string.theme_dark))
        ChoiceChips(ThemeMode.entries, p.theme, { labels.getValue(it) }, { t -> vm.update { it.copy(theme = t) } })
    }
    item { SwitchRow(stringResource(R.string.pref_high_contrast), p.highContrast, { v -> vm.update { it.copy(highContrast = v) } }, subtitle = stringResource(R.string.pref_high_contrast_sub), icon = Icons.Outlined.Contrast) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item { SwitchRow(stringResource(R.string.pref_dynamic_color), p.dynamicColor, { v -> vm.update { it.copy(dynamicColor = v) } }, subtitle = stringResource(R.string.pref_dynamic_color_sub), icon = Icons.Outlined.Wallpaper) }
    }
    item { SwitchRow(stringResource(R.string.pref_reduce_motion), p.reduceMotion, { v -> vm.update { it.copy(reduceMotion = v) } }, subtitle = stringResource(R.string.pref_reduce_motion_sub), icon = Icons.Outlined.Animation) }
}

@Composable
private fun ReminderSettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCapabilities() }
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    PrefsScreen(stringResource(R.string.settings_reminders), onBack) { p, svm ->
        item {
            HayatCard(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.rel_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.rel_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CapabilityRow(stringResource(R.string.rel_notifications), caps.notificationsEnabled, stringResource(R.string.rel_notifications_off)) { vm.openSystem(SystemSettingsTarget.APP_NOTIFICATIONS) }
                CapabilityRow(stringResource(R.string.rel_exact), caps.exactAlarmsAllowed, stringResource(R.string.rel_exact_off)) { vm.openSystem(SystemSettingsTarget.EXACT_ALARMS) }
                CapabilityRow(stringResource(R.string.rel_full_screen), caps.fullScreenAllowed, stringResource(R.string.rel_full_screen_off)) { vm.openSystem(SystemSettingsTarget.FULL_SCREEN_INTENT) }
                CapabilityRow(stringResource(R.string.rel_battery), caps.batteryUnrestricted, stringResource(R.string.rel_battery_on)) { vm.openSystem(SystemSettingsTarget.BATTERY_OPTIMIZATION) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.rel_force_stop_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        chips(R.string.pref_snooze) {
            val fmt = LocalFormatter.current
            val res = LocalContext.current.resources
            ChoiceChips(listOf(5, 10, 15, 30, 60), p.defaultSnoozeMinutes, { minutesLabel(res, it, fmt) }, { m -> svm.update { it.copy(defaultSnoozeMinutes = m) } })
        }
        item { SwitchRow(stringResource(R.string.pref_quiet_hours), p.quietHours.enabled, { v -> svm.update { it.copy(quietHours = it.quietHours.copy(enabled = v)) } }, subtitle = stringResource(R.string.pref_quiet_hours_sub), icon = Icons.Outlined.DoNotDisturbOn) }
        if (p.quietHours.enabled) item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(stringResource(UiR.string.label_window_start), p.quietHours.start, { t -> t?.let { svm.update { s -> s.copy(quietHours = s.quietHours.copy(start = it)) } } }, Modifier.weight(1f))
                TimeField(stringResource(UiR.string.label_window_end), p.quietHours.end, { t -> t?.let { svm.update { s -> s.copy(quietHours = s.quietHours.copy(end = it)) } } }, Modifier.weight(1f))
            }
        }
        item { SectionHeader(stringResource(R.string.pref_categories), Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        item { Text(stringResource(R.string.pref_categories_sub), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val cats = listOf(ReminderCategory.WATER, ReminderCategory.SLEEP, ReminderCategory.PRAYER)
        cats.forEach { c ->
            item {
                SwitchRow(stringResource(c.labelRes()), c in p.enabledReminderCategories, { on ->
                    svm.update(affectsReminders = true) { it.copy(enabledReminderCategories = if (on) it.enabledReminderCategories + c else it.enabledReminderCategories - c) }
                })
            }
        }
        item { SettingsRow(stringResource(R.string.pref_channel_settings), subtitle = stringResource(R.string.pref_channel_settings_sub), icon = Icons.Outlined.VolumeUp, onClick = { vm.openSystem(SystemSettingsTarget.APP_NOTIFICATIONS) }) }
    }
}

private fun minutesLabel(res: android.content.res.Resources, m: Int, fmt: com.hayat.core.ui.HayatFormatter): String = fmt.digits(res.getString(UiR.string.label_minutes, m))

@Composable
private fun CapabilityRow(label: String, ok: Boolean, problem: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!ok) Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ok) StatusChip(stringResource(R.string.rel_ok), StatusTone.SUCCESS, icon = Icons.Outlined.CheckCircle)
        else TextButton(onClick = onFix) { Text(stringResource(R.string.rel_fix)) }
    }
}

@Composable
private fun LifestyleSettingsScreen(onBack: () -> Unit, openPrayerSettings: () -> Unit) = PrefsScreen(stringResource(R.string.settings_lifestyle), onBack) { p, vm ->
    item { SwitchRow(stringResource(R.string.pref_islamic), p.islamicModuleEnabled, { v -> vm.update(true) { it.copy(islamicModuleEnabled = v) } }, subtitle = stringResource(R.string.pref_islamic_sub), icon = Icons.Outlined.Mosque) }
    if (p.islamicModuleEnabled) {
        item { SettingsRow(stringResource(R.string.pref_prayer_settings), icon = Icons.Outlined.Schedule, onClick = openPrayerSettings) }
        chips(R.string.pref_hijri_offset) {
            val fmt = LocalFormatter.current
            ChoiceChips((-2..2).toList(), p.hijriOffsetDays, { if (it > 0) fmt.digits("+$it") else fmt.digits("$it") }, { d -> vm.update { it.copy(hijriOffsetDays = d) } })
        }
        item {
            NumberField(p.quranDailyPagesGoal.takeIf { it > 0 }?.toString().orEmpty(), { v -> vm.update { it.copy(quranDailyPagesGoal = v.toIntOrNull()?.coerceIn(0, 604) ?: 0) } },
                stringResource(R.string.pref_quran_goal), Modifier.padding(horizontal = 16.dp))
        }
    }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionHeader(stringResource(R.string.section_water), Modifier.padding(horizontal = 16.dp)) }
    chips(R.string.pref_water_glass) {
        val fmt = LocalFormatter.current
        ChoiceChips(p.water.glassSizesMl, p.water.defaultGlassMl, { fmt.digits("$it ml") }, { g -> vm.update { it.copy(water = it.water.copy(defaultGlassMl = g)) } })
    }
    item { SwitchRow(stringResource(R.string.pref_water_reminders), p.water.reminderEnabled, { v -> vm.update(true) { it.copy(water = it.water.copy(reminderEnabled = v)) } }, icon = Icons.Outlined.WaterDrop) }
    if (p.water.reminderEnabled) chips(R.string.pref_water_interval) {
        val fmt = LocalFormatter.current
        val res = LocalContext.current.resources
        ChoiceChips(listOf(15, 30, 60, 90, 120, 180), p.water.reminderIntervalMinutes, { minutesLabel(res, it, fmt) }, { m -> vm.update(true) { it.copy(water = it.water.copy(reminderIntervalMinutes = m)) } })
    }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionHeader(stringResource(R.string.section_sleep), Modifier.padding(horizontal = 16.dp)) }
    item {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(stringResource(R.string.pref_wake_time), p.wakeTime, { t -> t?.let { vm.update(true) { s -> s.copy(wakeTime = it) } } }, Modifier.weight(1f))
            TimeField(stringResource(R.string.pref_sleep_time), p.sleepTime, { t -> t?.let { vm.update(true) { s -> s.copy(sleepTime = it) } } }, Modifier.weight(1f))
        }
    }
    chips(R.string.pref_sleep_goal) {
        val fmt = LocalFormatter.current
        ChoiceChips(listOf(360, 420, 480, 540), p.sleepGoalMinutes, { fmt.digits("${it / 60} h") }, { g -> vm.update { it.copy(sleepGoalMinutes = g) } })
    }
    item { SwitchRow(stringResource(R.string.pref_bedtime_reminder), p.bedtimeReminderEnabled, { v -> vm.update(true) { it.copy(bedtimeReminderEnabled = v) } }, subtitle = stringResource(R.string.pref_bedtime_reminder_sub), icon = Icons.Outlined.Bedtime) }
    item { SwitchRow(stringResource(R.string.pref_wake_alarm), p.wakeAlarmEnabled, { v -> vm.update(true) { it.copy(wakeAlarmEnabled = v) } }, subtitle = stringResource(R.string.pref_wake_alarm_sub), icon = Icons.Outlined.Alarm) }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionHeader(stringResource(R.string.section_focus), Modifier.padding(horizontal = 16.dp)) }
    chips(R.string.pref_focus_minutes) {
        val fmt = LocalFormatter.current
        val res = LocalContext.current.resources
        ChoiceChips(listOf(15, 20, 25, 30, 45, 50, 60), p.pomodoro.focusMinutes, { minutesLabel(res, it, fmt) }, { m -> vm.update { it.copy(pomodoro = it.pomodoro.copy(focusMinutes = m)) } })
    }
    chips(R.string.pref_short_break) {
        val fmt = LocalFormatter.current
        val res = LocalContext.current.resources
        ChoiceChips(listOf(3, 5, 10), p.pomodoro.shortBreakMinutes, { minutesLabel(res, it, fmt) }, { m -> vm.update { it.copy(pomodoro = it.pomodoro.copy(shortBreakMinutes = m)) } })
    }
    chips(R.string.pref_long_break) {
        val fmt = LocalFormatter.current
        val res = LocalContext.current.resources
        ChoiceChips(listOf(10, 15, 20, 30), p.pomodoro.longBreakMinutes, { minutesLabel(res, it, fmt) }, { m -> vm.update { it.copy(pomodoro = it.pomodoro.copy(longBreakMinutes = m)) } })
    }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionHeader(stringResource(R.string.section_finance), Modifier.padding(horizontal = 16.dp)) }
    chips(R.string.pref_currency) {
        ChoiceChips(listOf("BDT", "USD", "EUR", "GBP", "INR", "SAR", "AED", "MYR"), p.currency, { it }, { c -> vm.update { it.copy(currency = c) } })
    }
}

@Composable
private fun PrivacySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var showSetPin by rememberSaveable { mutableStateOf(false) }
    var showDisable by rememberSaveable { mutableStateOf(false) }
    var showDeleteAll by rememberSaveable { mutableStateOf(false) }
    PrefsScreen(stringResource(R.string.settings_privacy), onBack) { p, vm ->
        item { InfoBanner("${stringResource(R.string.privacy_offline_title)}\n${stringResource(R.string.privacy_offline_text)}", Modifier.padding(16.dp)) }
        item { SwitchRow(stringResource(R.string.pref_app_lock), p.appLock.enabled, { on -> if (on) showSetPin = true else showDisable = true }, subtitle = stringResource(R.string.pref_app_lock_sub), icon = Icons.Outlined.Lock) }
        if (p.appLock.enabled) {
            item {
                SwitchRow(stringResource(R.string.pref_biometric), p.appLock.biometricEnabled && biometricAvailable, { v -> vm.update { it.copy(appLock = it.appLock.copy(biometricEnabled = v)) } },
                    subtitle = if (biometricAvailable) null else stringResource(R.string.pref_biometric_unavailable), icon = Icons.Outlined.Fingerprint, enabled = biometricAvailable)
            }
            chips(R.string.pref_lock_timeout) {
                val labels = mapOf(0 to stringResource(R.string.timeout_immediately), 60 to stringResource(R.string.timeout_1m), 300 to stringResource(R.string.timeout_5m), 1800 to stringResource(R.string.timeout_30m))
                ChoiceChips(labels.keys.toList(), p.appLock.timeoutSeconds, { labels.getValue(it) }, { t -> vm.update { it.copy(appLock = it.appLock.copy(timeoutSeconds = t)) } })
            }
        }
        item { SwitchRow(stringResource(R.string.pref_secure_screen), p.appLock.secureScreen, { v -> vm.update { it.copy(appLock = it.appLock.copy(secureScreen = v)) } }, subtitle = stringResource(R.string.pref_secure_screen_sub), icon = Icons.Outlined.VisibilityOff) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SettingsRow(stringResource(R.string.delete_all), subtitle = stringResource(R.string.delete_all_sub), icon = Icons.Outlined.DeleteForever, onClick = { showDeleteAll = true }) }
        item {
            if (showSetPin) SetPinDialog(onDismiss = { showSetPin = false }) { a, b, err -> vm.enableAppLock(a, b) { e -> if (e == null) showSetPin = false else err(e) } }
            if (showDisable) VerifyPinDialog(onDismiss = { showDisable = false }) { pin, err -> vm.disableAppLock(pin) { r -> if (r == PinCheck.Correct) showDisable = false else err() } }
            if (showDeleteAll) ConfirmDialog(stringResource(R.string.delete_all_confirm_title), stringResource(R.string.delete_all_confirm_text),
                stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { vm.deleteAllData { showDeleteAll = false } }, { showDeleteAll = false }, destructive = true)
        }
    }
}

@Composable
private fun PinInput(value: String, onChange: (String) -> Unit, label: String, isError: Boolean) {
    OutlinedTextField(value, { v -> if (v.length <= 8 && v.all(Char::isDigit)) onChange(v) }, label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSubmit: (CharArray, CharArray, (Int) -> Unit) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinInput(a, { a = it; error = null }, stringResource(R.string.pin_hint), error != null)
                PinInput(b, { b = it; error = null }, stringResource(R.string.pin_confirm_title), error != null)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.pin_recovery_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(a.toCharArray(), b.toCharArray()) { error = it } }) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

@Composable
private fun VerifyPinDialog(onDismiss: () -> Unit, onSubmit: (CharArray, () -> Unit) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_app_lock)) },
        text = { PinInput(pin, { pin = it; wrong = false }, stringResource(R.string.pin_hint), wrong) },
        confirmButton = { TextButton(onClick = { onSubmit(pin.toCharArray()) { wrong = true; pin = "" } }) { Text(stringResource(UiR.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

@Composable
fun DashboardCard.title(): String = stringResource(
    when (this) {
        DashboardCard.TODAY_PROGRESS -> R.string.card_today_progress; DashboardCard.PRIORITIES -> R.string.card_priorities
        DashboardCard.NEXT_TASK -> R.string.card_next_task; DashboardCard.UPCOMING_REMINDERS -> R.string.card_upcoming
        DashboardCard.MISSED_REMINDERS -> R.string.card_missed; DashboardCard.WATER -> R.string.card_water
        DashboardCard.MEDICINE -> R.string.card_medicine; DashboardCard.PRAYER -> R.string.card_prayer
        DashboardCard.ZIKR -> R.string.card_zikr; DashboardCard.HABITS -> R.string.card_habits
        DashboardCard.DIET -> R.string.card_diet; DashboardCard.TIMELINE -> R.string.card_timeline
    },
)

@Composable
private fun DashboardSettingsScreen(onBack: () -> Unit) = PrefsScreen(stringResource(R.string.settings_dashboard), onBack) { p, vm ->
    itemsIndexed(p.dashboardCards, key = { _, c -> c.card.name }) { index, cfg ->
        val name = cfg.card.title()
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { vm.moveCard(cfg.card, -1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.dash_move_up, name)) }
            IconButton(onClick = { vm.moveCard(cfg.card, 1) }, enabled = index < p.dashboardCards.lastIndex) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.dash_move_down, name)) }
            Switch(checked = cfg.visible, onCheckedChange = { vm.setCardVisible(cfg.card, it) })
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit, version: String) {
    HayatScreen(title = stringResource(R.string.settings_about), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hayat", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(LocalFormatter.current.digits(stringResource(R.string.about_version, version)), style = MaterialTheme.typography.bodyMedium)
            HayatCard { Text(stringResource(R.string.privacy_offline_title), style = MaterialTheme.typography.titleSmall); Text(stringResource(R.string.privacy_offline_text), style = MaterialTheme.typography.bodySmall) }
            HayatCard { Text(stringResource(R.string.about_help), style = MaterialTheme.typography.titleSmall); Text(stringResource(R.string.about_help_text), style = MaterialTheme.typography.bodySmall) }
            InfoBanner(stringResource(R.string.about_medical))
            InfoBanner(stringResource(R.string.about_religious))
            SectionHeader(stringResource(R.string.about_licenses))
            Text(stringResource(R.string.license_font), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.license_adhan), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.license_androidx), style = MaterialTheme.typography.bodySmall)
        }
    }
}
