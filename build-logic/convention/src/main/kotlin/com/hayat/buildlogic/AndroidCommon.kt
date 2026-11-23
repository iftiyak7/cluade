package com.hayat.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Applies SDK levels, Java level, test options and lint policy to any Android module. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version = release(libs.int("compileSdk")) {
                minorApiLevel = libs.int("compileSdkMinor")
            }
        }
        defaultConfig.minSdk = libs.int("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
        lint.abortOnError = true
        lint.checkReleaseBuilds = true
        lint.warningsAsErrors = false
        lint.disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
    }
    configureKotlin()
    tasks.withType<Test>().configureEach {
        maxHeapSize = "2g"
        systemProperty("robolectric.logging.enabled", "false")
    }
    dependencies {
        add("testImplementation", libs.lib("junit4"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
    }
}
