package com.hayat.app

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.hayat.feature.onboarding.onboardingGraph
import com.hayat.feature.tasks.tasksGraph
import com.hayat.feature.routines.routinesGraph
import com.hayat.feature.reminders.remindersGraph
import com.hayat.feature.calendar.calendarGraph
import com.hayat.feature.water.waterGraph
import com.hayat.feature.medicine.medicineGraph
import com.hayat.feature.diet.dietGraph
import com.hayat.feature.sleep.sleepGraph
import com.hayat.feature.fitness.fitnessGraph
import com.hayat.feature.ibadah.ibadahGraph
import com.hayat.feature.habits.habitsGraph
import com.hayat.feature.study.studyGraph
import com.hayat.feature.finance.financeGraph
import com.hayat.feature.events.eventsGraph
import com.hayat.feature.notes.notesGraph
import com.hayat.feature.dashboard.dashboardGraph
import com.hayat.feature.analytics.analyticsGraph
import com.hayat.feature.search.searchGraph
import com.hayat.feature.backup.backupGraph
import com.hayat.feature.settings.settingsGraph

/** Every feature registers its destinations here; features never reference each other. */
fun NavGraphBuilder.hayatGraphs(nav: NavController, appVersion: String) {
    onboardingGraph(nav)
    tasksGraph(nav)
    routinesGraph(nav)
    remindersGraph(nav)
    calendarGraph(nav)
    waterGraph(nav)
    medicineGraph(nav)
    dietGraph(nav)
    sleepGraph(nav)
    fitnessGraph(nav)
    ibadahGraph(nav)
    habitsGraph(nav)
    studyGraph(nav)
    financeGraph(nav)
    eventsGraph(nav)
    notesGraph(nav)
    dashboardGraph(nav)
    analyticsGraph(nav)
    searchGraph(nav)
    backupGraph(nav)
    settingsGraph(nav, appVersion)
}
