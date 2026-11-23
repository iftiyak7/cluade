plugins {
    alias(libs.plugins.hayat.android.library)
    alias(libs.plugins.hayat.android.compose)
}

android { namespace = "com.hayat.core.designsystem" }

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
}
