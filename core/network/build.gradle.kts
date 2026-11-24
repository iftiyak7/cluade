plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// The only module that performs network I/O: the anonymous announcements feed (docs/privacy.md).
android { namespace = "com.hayat.core.network" }

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.notifications)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(projects.core.testing)
}
