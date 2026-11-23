plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.hayat.core.backup" }

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.security)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.data)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
