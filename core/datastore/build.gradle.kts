plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.hayat.core.datastore" }

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
}
