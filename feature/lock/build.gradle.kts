plugins { alias(libs.plugins.hayat.android.feature) }

android { namespace = "com.hayat.feature.lock" }

dependencies {
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
}
