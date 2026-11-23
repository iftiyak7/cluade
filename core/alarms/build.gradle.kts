plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.android.compose)
    alias(libs.plugins.hayat.hilt)
}

android { namespace = "com.hayat.core.alarms" }

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.notifications)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}
