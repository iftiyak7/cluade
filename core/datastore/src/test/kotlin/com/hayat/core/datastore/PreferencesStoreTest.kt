package com.hayat.core.datastore

import com.hayat.core.model.AppLanguage
import com.hayat.core.model.DashboardCard
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerLocation
import com.hayat.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalTime

class PreferencesStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `round trips and persists across instances`() = runTest(UnconfinedTestDispatcher()) {
        val file = tmp.newFile("prefs.json").also { it.delete() }
        val scope1 = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = DataStorePreferencesRepository(createPreferencesStore({ file }, scope1.backgroundScope))
        repo.update {
            it.copy(
                language = AppLanguage.BENGALI, theme = ThemeMode.DARK, wakeTime = LocalTime.of(5, 15), islamicModuleEnabled = true,
                prayer = it.prayer.copy(location = PrayerLocation("Dhaka", 23.81, 90.41, "Asia/Dhaka"), remindersEnabled = setOf(Prayer.FAJR)),
                dashboardCards = it.dashboardCards.reversed().map { c -> if (c.card == DashboardCard.WATER) c.copy(visible = false) else c },
            )
        }
        val read = repo.current()
        assertEquals(AppLanguage.BENGALI, read.language)
        assertEquals(LocalTime.of(5, 15), read.wakeTime)
        assertEquals(setOf(Prayer.FAJR), read.prayer.remindersEnabled)
        assertFalse(read.dashboardCards.first { it.card == DashboardCard.WATER }.visible)
        assertEquals(DashboardCard.entries.last(), read.dashboardCards.first().card)
        assertTrue(file.exists())
    }

    @Test fun `corrupt file falls back to defaults`() = runTest(UnconfinedTestDispatcher()) {
        val file = tmp.newFile("bad.json").apply { writeText("{not json") }
        val repo = DataStorePreferencesRepository(createPreferencesStore({ file }, backgroundScope))
        assertEquals(ThemeMode.SYSTEM, repo.preferences.first().theme)
    }

    @Test fun `unknown enum values are tolerated`() {
        val dto = PreferencesDto(theme = "PURPLE", language = "xx", dashboardCards = listOf(CardDto("REMOVED_CARD", true)))
        val m = dto.toModel()
        assertEquals(ThemeMode.SYSTEM, m.theme)
        assertEquals(DashboardCard.entries.size, m.dashboardCards.size)
    }
}
