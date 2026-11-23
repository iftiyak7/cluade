plugins {
    `kotlin-dsl`
}

group = "com.hayat.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "hayat.android.application"
            implementationClass = "com.hayat.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "hayat.android.library"
            implementationClass = "com.hayat.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "hayat.android.compose"
            implementationClass = "com.hayat.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "hayat.android.feature"
            implementationClass = "com.hayat.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "hayat.android.room"
            implementationClass = "com.hayat.buildlogic.AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = "hayat.hilt"
            implementationClass = "com.hayat.buildlogic.HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "hayat.jvm.library"
            implementationClass = "com.hayat.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
