plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.hilt)
}

android { namespace = "com.hayat.core.notifications" }

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
