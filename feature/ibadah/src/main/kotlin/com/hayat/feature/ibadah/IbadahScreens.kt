package com.hayat.feature.ibadah

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.CounterButton
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.KeyValueRow
import com.hayat.core.designsystem.component.LinearMeter
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.worship.PrayerCities
import com.hayat.core.domain.worship.QuranData
import com.hayat.core.model.AsrMadhab
import com.hayat.core.model.FastingStatus
import com.hayat.core.model.FastingType
import com.hayat.core.model.HighLatitudeRule
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerCalcMethod
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.ZikrDefinition
import com.hayat.core.navigation.IbadahRoute
import com.hayat.core.navigation.PrayerSettingsRoute
import com.hayat.core.navigation.ZikrCounterRoute
import com.hayat.core.navigation.ZikrEditRoute
import com.hayat.core.time.HijriCalendar
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.labelRes
import com.hayat.core.ui.R as UiR

fun NavGraphBuilder.ibadahGraph(nav: NavController) {
    composable<IbadahRoute> { IbadahScreen(nav) }
    composable<ZikrCounterRoute> { ZikrCounterScreen(nav) }
    composable<ZikrEditRoute> { ZikrEditScreen(nav::popBackStack) }
    composable<PrayerSettingsRoute> { PrayerSettingsScreen(nav::popBackStack) }
}

@Composable
fun zikrTitle(z: ZikrDefinition): String = when (z.systemKey) {
    "subhanallah" -> stringResource(R.string.zk_subhanallah); "alhamdulillah" -> stringResource(R.string.zk_alhamdulillah)
    "allahuakbar" -> stringResource(R.string.zk_allahuakbar); "tahlil" -> stringResource(R.string.zk_tahlil)
    "istighfar" -> stringResource(R.string.zk_istighfar); "durood" -> stringResource(R.string.zk_durood)
    "subhanallahi_wa_bihamdihi" -> stringResource(R.string.zk_swb); else -> z.title
}

@Composable
fun zikrMeaning(z: ZikrDefinition): String = when (z.systemKey) {
    "subhanallah" -> stringResource(R.string.zk_subhanallah_m); "alhamdulillah" -> stringResource(R.string.zk_alhamdulillah_m)
    "allahuakbar" -> stringResource(R.string.zk_allahuakbar_m); "tahlil" -> stringResource(R.string.zk_tahlil_m)
    "istighfar" -> stringResource(R.string.zk_istighfar_m); "durood" -> stringResource(R.string.zk_durood_m)
    "subhanallahi_wa_bihamdihi" -> stringResource(R.string.zk_swb_m); else -> z.meaning
}

@Composable
private fun IbadahScreen(nav: NavController, vm: IbadahViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    HayatScreen(title = stringResource(R.string.ib_title), onBack = nav::popBackStack,
        actions = { IconButton(onClick = { nav.navigate(PrayerSettingsRoute) }) { Icon(Icons.Outlined.Tune, stringResource(R.string.ib_settings)) } }) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        if (!s.prefs.islamicModuleEnabled) {
            EmptyState(Icons.Outlined.Mosque, stringResource(R.string.ib_disabled_title), stringResource(R.string.ib_disabled_text), Modifier.padding(pad)) {
                Button(onClick = vm::enableModule) { Text(stringResource(R.string.ib_enable)) }
            }
            return@HayatScreen
        }
        Column(Modifier.padding(pad)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                listOf(R.string.ib_tab_today, R.string.ib_tab_zikr, R.string.ib_tab_quran, R.string.ib_tab_fasting).forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(stringResource(t)) }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> todayTab(s, nav, vm)
                    1 -> zikrTab(s, nav)
                    2 -> quranTab(s, vm)
                    3 -> fastingTab(s, vm)
                }
            }
        }
    }
}

private fun PrayerStatus.tone() = when (this) {
    PrayerStatus.PRAYED -> StatusTone.SUCCESS; PrayerStatus.PRAYED_LATE, PrayerStatus.QADA -> StatusTone.WARNING
    PrayerStatus.MISSED -> StatusTone.ERROR; PrayerStatus.EXCUSED -> StatusTone.NEUTRAL
}

private fun LazyListScope.todayTab(s: IbadahState, nav: NavController, vm: IbadahViewModel) {
    item {
        val fmt = LocalFormatter.current
        val h = HijriCalendar.fromGregorian(s.today, s.prefs.hijriOffsetDays)
        Text(fmt.date(s.today) + (h?.let { " · " + fmt.digits("${it.day}/${it.month}/${it.year}") } ?: ""), style = MaterialTheme.typography.titleMedium)
        if (h?.isRamadan == true) Text(stringResource(R.string.ib_ramadan_day, fmt.number(h.day)), color = MaterialTheme.colorScheme.primary)
    }
    val times = s.times
    if (times == null) {
        item {
            InfoBanner(stringResource(R.string.ib_set_location) + "\n" + stringResource(R.string.ib_set_location_text)) {
                TextButton(onClick = { nav.navigate(PrayerSettingsRoute) }) { Text(stringResource(R.string.ib_settings)) }
            }
        }
    } else {
        item {
            val fmt = LocalFormatter.current
            HayatCard {
                Text(stringResource(R.string.ib_prayer_times) + " · " + (s.prefs.prayer.location?.name ?: ""), style = MaterialTheme.typography.titleSmall)
                s.next?.let { (p, t) -> Text(stringResource(R.string.ib_next_prayer, stringResource(p.labelRes()), fmt.time(t.toLocalTime())), color = MaterialTheme.colorScheme.primary) }
                KeyValueRow(stringResource(UiR.string.prayer_fajr), fmt.time(times.fajr.toLocalTime()))
                KeyValueRow(stringResource(UiR.string.prayer_sunrise), fmt.time(times.sunrise.toLocalTime()))
                KeyValueRow(stringResource(UiR.string.prayer_dhuhr), fmt.time(times.dhuhr.toLocalTime()))
                KeyValueRow(stringResource(UiR.string.prayer_asr), fmt.time(times.asr.toLocalTime()))
                KeyValueRow(stringResource(UiR.string.prayer_maghrib), fmt.time(times.maghrib.toLocalTime()))
                KeyValueRow(stringResource(UiR.string.prayer_isha), fmt.time(times.isha.toLocalTime()))
                Text(stringResource(R.string.ib_times_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item { Text(LocalFormatter.current.digits(stringResource(R.string.ib_prayers_logged, s.prayerLogs.count { it.status != PrayerStatus.MISSED })), style = MaterialTheme.typography.titleSmall) }
    items(Prayer.entries) { p -> PrayerLogRow(p, s.prayerLogs.firstOrNull { it.prayer == p }?.status) { vm.setPrayer(p, it) } }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PrayerLogRow(p: Prayer, status: PrayerStatus?, onSet: (PrayerStatus?) -> Unit) {
    HayatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(p.labelRes()), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            status?.let { StatusChip(stringResource(it.labelRes()), it.tone()) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PrayerStatus.entries.forEach { st -> FilterChip(status == st, { onSet(if (status == st) null else st) }, label = { Text(stringResource(st.labelRes())) }) }
        }
    }
}

private fun LazyListScope.zikrTab(s: IbadahState, nav: NavController) {
    item { TextButton(onClick = { nav.navigate(ZikrEditRoute()) }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ib_zikr_add)) } }
    items(s.zikrs, key = { it.id }) { z ->
        val count = s.zikrToday[z.id] ?: 0
        val fmt = LocalFormatter.current
        HayatCard(onClick = { nav.navigate(ZikrCounterRoute(z.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(zikrTitle(z), style = MaterialTheme.typography.titleSmall)
                    if (z.arabic.isNotBlank()) Text(z.arabic, style = MaterialTheme.typography.titleMedium)
                }
                if (z.isFavorite) Icon(Icons.Outlined.Star, stringResource(R.string.ib_favorite), tint = MaterialTheme.colorScheme.primary)
            }
            val desc = stringResource(R.string.ib_zikr_today, fmt.number(count), fmt.number(z.targetCount))
            LinearMeter(count.toFloat() / z.targetCount, description = desc)
            Text(desc, style = MaterialTheme.typography.labelMedium)
        }
    }
    item { InfoBanner(stringResource(R.string.ib_content_note)) }
}

private fun LazyListScope.quranTab(s: IbadahState, vm: IbadahViewModel) {
    item {
        val fmt = LocalFormatter.current
        val pages = s.quran.filter { it.date == s.today }.sumOf { it.pages ?: 0.0 }
        HayatCard {
            if (s.prefs.quranDailyPagesGoal > 0) {
                val desc = stringResource(R.string.ib_quran_goal, fmt.decimal(pages), fmt.number(s.prefs.quranDailyPagesGoal))
                Text(desc, style = MaterialTheme.typography.titleSmall)
                LinearMeter((pages / s.prefs.quranDailyPagesGoal).toFloat(), description = desc)
            } else Text(stringResource(R.string.ib_quran_no_goal), style = MaterialTheme.typography.bodySmall)
        }
    }
    item { QuranLogForm(vm::logQuran) }
    item { SectionHeader(stringResource(R.string.ib_quran_history)) }
    if (s.quran.isEmpty()) item { Text(stringResource(R.string.ib_quran_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(s.quran, key = { it.id }) { l ->
        val fmt = LocalFormatter.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmt.dayMonth(l.date) + (l.surah?.let { n -> " · " + fmt.number(n) + ". " + (QuranData.surah(n)?.nameTransliterated ?: "") } ?: ""))
                Text(listOfNotNull(
                    if (l.fromAyah != null && l.toAyah != null) fmt.digits("${l.fromAyah}–${l.toAyah}") else null,
                    l.pages?.let { fmt.decimal(it) + " " + stringResource(R.string.ib_pages) },
                    l.minutes?.let { fmt.number(it) + " " + stringResource(R.string.ib_minutes) },
                ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { vm.deleteQuran(l) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
        }
    }
}

@Composable
private fun QuranLogForm(onLog: (Int?, Int?, Int?, Double?, Int?) -> Unit) {
    var surah by rememberSaveable { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    val fmt = LocalFormatter.current
    HayatCard {
        Text(stringResource(R.string.ib_quran_log), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { menu = true }) {
            Text(surah?.let { n -> fmt.number(n) + ". " + QuranData.surah(n)?.nameTransliterated + " (" + stringResource(R.string.ib_ayahs, fmt.number(QuranData.surah(n)!!.ayahCount)) + ")" } ?: stringResource(R.string.ib_surah))
        }
        DropdownMenu(menu, { menu = false }) {
            QuranData.surahs.forEach { s -> DropdownMenuItem(text = { Text(fmt.number(s.number) + ". " + s.nameTransliterated) }, onClick = { surah = s.number; menu = false }) }
        }
        val max = surah?.let { QuranData.surah(it)?.ayahCount } ?: 286
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(from, { v -> if ((v.toIntOrNull() ?: 0) <= max) from = v }, stringResource(R.string.ib_from_ayah), Modifier.weight(1f))
            NumberField(to, { v -> if ((v.toIntOrNull() ?: 0) <= max) to = v }, stringResource(R.string.ib_to_ayah), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(pages, { pages = it }, stringResource(R.string.ib_pages), Modifier.weight(1f), decimal = true)
            NumberField(minutes, { minutes = it }, stringResource(R.string.ib_minutes), Modifier.weight(1f))
        }
        Button(onClick = {
            val f = from.toIntOrNull(); val t = to.toIntOrNull()
            onLog(surah, f, t?.let { if (f != null && it < f) f else it }, pages.replace(',', '.').toDoubleOrNull(), minutes.toIntOrNull())
            from = ""; to = ""; pages = ""; minutes = ""
        }) { Text(stringResource(UiR.string.action_log)) }
    }
}

private fun LazyListScope.fastingTab(s: IbadahState, vm: IbadahViewModel) {
    item {
        Text(LocalFormatter.current.digits(stringResource(R.string.ib_fasting_count, s.fasting.count { it.status == FastingStatus.FASTED })), style = MaterialTheme.typography.titleSmall)
    }
    val days = generateSequence(s.today.withDayOfMonth(1)) { it.plusDays(1) }.takeWhile { it.month == s.today.month }.toList()
    items(days.filter { !it.isAfter(s.today.plusDays(7)) }.reversed(), key = { it.toString() }) { d ->
        val log = s.fasting.firstOrNull { it.date == d }
        FastingRow(d, log?.type ?: if (HijriCalendar.fromGregorian(d, s.prefs.hijriOffsetDays)?.isRamadan == true) FastingType.RAMADAN else FastingType.NAFL, log?.status, d == s.today) { type, status -> vm.setFasting(d, type, status) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FastingRow(date: java.time.LocalDate, type: FastingType, status: FastingStatus?, isToday: Boolean, onSet: (FastingType, FastingStatus?) -> Unit) {
    val fmt = LocalFormatter.current
    HayatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.dayMonth(date) + if (isToday) " · " + stringResource(R.string.ib_fasting_today) else "", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            status?.let { StatusChip(stringResource(it.labelRes()), if (it == FastingStatus.FASTED) StatusTone.SUCCESS else StatusTone.NEUTRAL) }
        }
        val typeLabels = FastingType.entries.associateWith { stringResource(it.labelRes()) }
        ChoiceChips(FastingType.entries, type, { typeLabels.getValue(it) }, { onSet(it, status ?: FastingStatus.FASTED) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FastingStatus.entries.forEach { st -> FilterChip(status == st, { onSet(type, if (status == st) null else st) }, label = { Text(stringResource(st.labelRes())) }) }
        }
    }
}

@Composable
private fun ZikrCounterScreen(nav: NavController, vm: ZikrCounterViewModel = hiltViewModel()) {
    val zikr by vm.zikr.collectAsStateWithLifecycle()
    val count by vm.todayCount.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val vibrate by vm.vibrate.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    val z = zikr
    HayatScreen(title = z?.let { zikrTitle(it) }.orEmpty(), onBack = nav::popBackStack,
        actions = {
            if (z != null) {
                IconButton(onClick = vm::toggleFavorite) { Icon(if (z.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, stringResource(R.string.ib_favorite)) }
                IconButton(onClick = { nav.navigate(ZikrEditRoute(z.id)) }) { Icon(Icons.Outlined.Edit, stringResource(UiR.string.action_edit)) }
            }
        }) { pad ->
        if (z == null) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (z.arabic.isNotBlank()) Text(z.arabic, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (z.transliteration.isNotBlank()) Text(z.transliteration, style = MaterialTheme.typography.bodyLarge)
            zikrMeaning(z).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            CounterButton(
                count = fmt.number(count), label = stringResource(R.string.ib_tap_count),
                onClick = { vm.add(1); if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                contentDescription = stringResource(R.string.ib_count_cd, fmt.number(count), fmt.number(z.targetCount)),
            )
            LinearMeter(count.toFloat() / z.targetCount, description = stringResource(R.string.ib_zikr_today, fmt.number(count), fmt.number(z.targetCount)))
            if (count >= z.targetCount) StatusChip(stringResource(R.string.ib_target_reached), StatusTone.SUCCESS, icon = Icons.Outlined.CheckCircle)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.add(-1) }, enabled = count > 0) { Icon(Icons.Outlined.Remove, null); Text(stringResource(R.string.ib_minus_one)) }
                OutlinedButton(onClick = { confirmReset = true }, enabled = count > 0) { Icon(Icons.Outlined.RestartAlt, null); Text(stringResource(R.string.ib_reset)) }
            }
            SwitchRow(stringResource(R.string.ib_vibrate), vibrate, { vm.vibrate.value = it })
            SectionHeader(stringResource(R.string.ib_week))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                vm.days().forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt.weekdayNarrow(d.dayOfWeek), style = MaterialTheme.typography.labelSmall)
                        Text(fmt.number(week.firstOrNull { it.date == d }?.count ?: 0), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    if (confirmReset) ConfirmDialog(stringResource(R.string.ib_reset), stringResource(R.string.ib_reset_confirm), stringResource(UiR.string.action_reset),
        stringResource(UiR.string.action_cancel), { confirmReset = false; vm.reset() }, { confirmReset = false }, destructive = true)
}

@Composable
private fun ZikrEditScreen(onBack: () -> Unit, vm: ZikrEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    val z = form
    HayatScreen(title = stringResource(if (vm.isNew) R.string.ib_zikr_new else R.string.ib_zikr_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (!vm.isNew) IconButton(onClick = vm::delete) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (z == null) { LoadingBox(); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(z.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), maxLength = 120)
            FormTextField(z.arabic, { v -> vm.edit { it.copy(arabic = v) } }, stringResource(R.string.ib_zikr_arabic), singleLine = false, maxLength = 500)
            FormTextField(z.transliteration, { v -> vm.edit { it.copy(transliteration = v) } }, stringResource(R.string.ib_zikr_translit), singleLine = false, maxLength = 500)
            FormTextField(z.meaning, { v -> vm.edit { it.copy(meaning = v) } }, stringResource(R.string.ib_zikr_meaning), singleLine = false, maxLength = 500)
            NumberField(z.targetCount.toString(), { v -> v.toIntOrNull()?.let { t -> vm.edit { it.copy(targetCount = t) } } }, stringResource(R.string.ib_zikr_target))
            InfoBanner(stringResource(R.string.ib_content_note))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrayerCalcMethod.label() = stringResource(when (this) {
    PrayerCalcMethod.KARACHI -> R.string.m_karachi; PrayerCalcMethod.MUSLIM_WORLD_LEAGUE -> R.string.m_mwl; PrayerCalcMethod.EGYPTIAN -> R.string.m_egyptian
    PrayerCalcMethod.UMM_AL_QURA -> R.string.m_umm; PrayerCalcMethod.DUBAI -> R.string.m_dubai; PrayerCalcMethod.QATAR -> R.string.m_qatar
    PrayerCalcMethod.KUWAIT -> R.string.m_kuwait; PrayerCalcMethod.MOON_SIGHTING_COMMITTEE -> R.string.m_msc; PrayerCalcMethod.SINGAPORE -> R.string.m_singapore
    PrayerCalcMethod.NORTH_AMERICA -> R.string.m_na
})

@Composable
private fun PrayerSettingsScreen(onBack: () -> Unit, vm: PrayerSettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var manual by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.ps_title), onBack = onBack, snackbarHostState = host) { pad ->
        val p = settings
        if (p == null) { LoadingBox(); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBanner(stringResource(R.string.ps_explain))
            SectionHeader(stringResource(R.string.ps_location))
            p.location?.let { Text(it.name + " · " + fmt.digits("%.3f, %.3f".format(java.util.Locale.ROOT, it.latitude, it.longitude)) + " · " + it.zoneId, style = MaterialTheme.typography.bodyMedium) }
            CityPicker(p.location?.name) { city -> vm.update { it.copy(location = city) } }
            TextButton(onClick = { manual = !manual }) { Text(stringResource(R.string.ps_manual)) }
            if (manual) ManualLocation(vm)
            preview?.let { t ->
                HayatCard {
                    Prayer.entries.forEach { pr -> KeyValueRow(stringResource(pr.labelRes()), fmt.time(t.timeOf(pr).toLocalTime())) }
                }
            }
            SectionHeader(stringResource(R.string.ps_method))
            val methodLabels = PrayerCalcMethod.entries.associateWith { it.label() }
            ChoiceChips(PrayerCalcMethod.entries, p.method, { methodLabels.getValue(it) }, { m -> vm.update { it.copy(method = m) } })
            SectionHeader(stringResource(R.string.ps_madhab))
            val madhabs = mapOf(AsrMadhab.SHAFI to stringResource(R.string.ps_shafi), AsrMadhab.HANAFI to stringResource(R.string.ps_hanafi))
            ChoiceChips(AsrMadhab.entries, p.madhab, { madhabs.getValue(it) }, { m -> vm.update { it.copy(madhab = m) } })
            SectionHeader(stringResource(R.string.ps_high_lat))
            val hl = mapOf(HighLatitudeRule.MIDDLE_OF_THE_NIGHT to stringResource(R.string.ps_hl_middle), HighLatitudeRule.SEVENTH_OF_THE_NIGHT to stringResource(R.string.ps_hl_seventh), HighLatitudeRule.TWILIGHT_ANGLE to stringResource(R.string.ps_hl_angle))
            ChoiceChips(HighLatitudeRule.entries, p.highLatitudeRule, { hl.getValue(it) }, { r -> vm.update { it.copy(highLatitudeRule = r) } })
            SectionHeader(stringResource(R.string.ps_adjust))
            Prayer.entries.forEach { pr ->
                val adj = p.adjustments[pr] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(pr.labelRes()), Modifier.weight(1f))
                    IconButton(onClick = { vm.update { it.copy(adjustments = it.adjustments + (pr to (adj - 1).coerceAtLeast(-30))) } }) { Icon(Icons.Outlined.Remove, stringResource(R.string.ib_minus_one)) }
                    Text(fmt.digits(if (adj > 0) "+$adj" else "$adj"), Modifier.width(40.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { vm.update { it.copy(adjustments = it.adjustments + (pr to (adj + 1).coerceAtMost(30))) } }) { Icon(Icons.Outlined.Add, stringResource(UiR.string.action_add)) }
                }
            }
            SectionHeader(stringResource(R.string.ps_reminders))
            Prayer.entries.forEach { pr ->
                SwitchRow(stringResource(pr.labelRes()), pr in p.remindersEnabled, { on -> vm.update { it.copy(remindersEnabled = if (on) it.remindersEnabled + pr else it.remindersEnabled - pr) } })
            }
            Text(stringResource(R.string.ps_offset), style = MaterialTheme.typography.titleSmall)
            val ctx = androidx.compose.ui.platform.LocalContext.current
            ChoiceChips(listOf(0, 5, 10, 15, 30), p.reminderOffsetMinutes, { fmt.digits(ctx.getString(UiR.string.label_minutes, it)) }, { m -> vm.update { it.copy(reminderOffsetMinutes = m) } })
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CityPicker(current: String?, onPick: (com.hayat.core.model.PrayerLocation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Icon(Icons.Outlined.LocationCity, null); Spacer(Modifier.width(6.dp)); Text(current ?: stringResource(R.string.ps_city)) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.ps_city)) },
        text = {
            LazyColumn {
                items(PrayerCities.all) { c ->
                    TextButton(onClick = { onPick(c); open = false }, modifier = Modifier.fillMaxWidth()) { Text(c.name, Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(UiR.string.action_close)) } })
}

@Composable
private fun ManualLocation(vm: PrayerSettingsViewModel) {
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf(java.time.ZoneId.systemDefault().id) }
    var name by rememberSaveable { mutableStateOf("") }
    HayatCard {
        OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(UiR.string.label_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it.take(12) }, label = { Text(stringResource(R.string.ps_lat)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(lon, { lon = it.take(12) }, label = { Text(stringResource(R.string.ps_lon)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(zone, { zone = it.take(40) }, label = { Text(stringResource(R.string.ps_zone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.setManual(lat, lon, zone, name) }) { Text(stringResource(R.string.ps_use_manual)) }
    }
}
