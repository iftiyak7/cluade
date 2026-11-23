plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.hilt)
}

android { namespace = "com.hayat.core.data" }

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
}
