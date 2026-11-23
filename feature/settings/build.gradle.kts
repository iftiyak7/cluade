plugins { alias(libs.plugins.hayat.android.feature) }

android { namespace = "com.hayat.feature.settings" }

dependencies {
    implementation(libs.androidx.biometric)
}
