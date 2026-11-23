plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.hilt)
}

android { namespace = "com.hayat.core.security" }

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.core.testing)
}
