package com.hayat.app

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.hayat.feature.settings.settingsGraph

/** Every feature registers its destinations here; features never reference each other. */
fun NavGraphBuilder.hayatGraphs(nav: NavController, appVersion: String) {
    settingsGraph(nav, appVersion)
}
