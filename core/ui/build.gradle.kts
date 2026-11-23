plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.android.compose)
}

android { namespace = "com.hayat.core.ui" }

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.time)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
