plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.android.room)
    alias(libs.plugins.hayat.hilt)
}

android {
    namespace = "com.hayat.core.database"
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
}
