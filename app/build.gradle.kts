import java.util.Properties

plugins {
    alias(libs.plugins.hayat.android.application)
    alias(libs.plugins.hayat.android.compose)
    alias(libs.plugins.hayat.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from an untracked keystore.properties (see docs/release.md). Never commit secrets.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use(::load)
}

android {
    namespace = "com.hayat.app"
    defaultConfig {
        applicationId = "com.hayat.app"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Announcements feed (HTTPS only). Empty = feature inactive. Set in gradle.properties or with
        // -PhayatFeedUrl=https://your-domain/api/v1/announcements
        val feedUrl = (project.findProperty("hayatFeedUrl") as String?).orEmpty()
        require(feedUrl.isEmpty() || feedUrl.startsWith("https://")) { "hayatFeedUrl must use https://" }
        buildConfigField("String", "FEED_URL", "\"$feedUrl\"")
    }
    androidResources {
        localeFilters += listOf("en", "bn")
        generateLocaleConfig = true
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.time)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    implementation(projects.core.notifications)
    implementation(projects.core.alarms)
    implementation(projects.core.security)
    implementation(projects.core.backup)
    implementation(projects.core.network)

    implementation(projects.feature.settings)
    implementation(projects.feature.lock)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.tasks)
    implementation(projects.feature.routines)
    implementation(projects.feature.reminders)
    implementation(projects.feature.calendar)
    implementation(projects.feature.water)
    implementation(projects.feature.medicine)
    implementation(projects.feature.diet)
    implementation(projects.feature.sleep)
    implementation(projects.feature.fitness)
    implementation(projects.feature.ibadah)
    implementation(projects.feature.habits)
    implementation(projects.feature.study)
    implementation(projects.feature.finance)
    implementation(projects.feature.events)
    implementation(projects.feature.notes)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.analytics)
    implementation(projects.feature.search)
    implementation(projects.feature.backup)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

// Opt-in screenshot capture for the website: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotCapture*' -PscreenshotsDir=/abs/path
val screenshotsDir = providers.gradleProperty("screenshotsDir").orElse("")
tasks.withType<Test>().configureEach {
    systemProperty("hayat.screenshots.dir", screenshotsDir.get())
}
