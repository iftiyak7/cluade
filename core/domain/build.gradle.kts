plugins { alias(libs.plugins.hayat.jvm.library) }

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.time)
    implementation(libs.adhan)
    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
}
