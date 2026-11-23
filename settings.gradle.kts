enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hayat"

// Every module listed here must exist on disk.
listOf(
    ":app",
    ":core:common",
    ":core:model",
    ":core:time",
    ":core:domain",
    ":core:database",
    ":core:datastore",
    ":core:data",
    ":core:designsystem",
    ":core:ui",
    ":core:navigation",
    ":core:notifications",
    ":core:alarms",
    ":core:security",
    ":core:backup",
    ":core:testing",
    ":feature:onboarding",
    ":feature:dashboard",
    ":feature:calendar",
    ":feature:tasks",
    ":feature:routines",
    ":feature:reminders",
    ":feature:water",
    ":feature:medicine",
    ":feature:diet",
    ":feature:sleep",
    ":feature:fitness",
    ":feature:ibadah",
    ":feature:habits",
    ":feature:study",
    ":feature:finance",
    ":feature:events",
    ":feature:notes",
    ":feature:analytics",
    ":feature:templates",
    ":feature:search",
    ":feature:backup",
    ":feature:settings",
    ":feature:lock",
).filter { file(it.removePrefix(":").replace(':', '/')).resolve("build.gradle.kts").exists() }
    .forEach { include(it) }
