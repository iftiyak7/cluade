plugins { alias(libs.plugins.hayat.jvm.library) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
