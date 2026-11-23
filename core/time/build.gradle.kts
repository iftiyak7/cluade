plugins { alias(libs.plugins.hayat.jvm.library) }

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
}
