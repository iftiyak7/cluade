package com.hayat.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature modules: presentation layer only. They may depend on domain contracts, UI kit and
 * navigation routes — never on :core:database, :core:data or :core:alarms.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("hayat.android.library")
        pluginManager.apply("hayat.android.compose")
        pluginManager.apply("hayat.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:time"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:navigation"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("androidx-activity-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("mockk"))
        }
    }
}
