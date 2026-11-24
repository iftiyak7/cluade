package com.hayat.app

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.hayat.feature.onboarding.onboardingGraph
import com.hayat.feature.tasks.tasksGraph
import com.hayat.feature.routines.routinesGraph
import com.hayat.feature.reminders.remindersGraph
import com.hayat.feature.calendar.calendarGraph
import com.hayat.feature.settings.settingsGraph

/** Every feature registers its destinations here; features never reference each other. */
fun NavGraphBuilder.hayatGraphs(nav: NavController, appVersion: String) {
    onboardingGraph(nav)
    tasksGraph(nav)
    routinesGraph(nav)
    remindersGraph(nav)
    calendarGraph(nav)
    settingsGraph(nav, appVersion)
}
