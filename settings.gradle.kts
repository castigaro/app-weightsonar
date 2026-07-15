pluginManagement {
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

rootProject.name = "WeightSonar"
include(":app")

// Gemeinsame Bibliothek der AppSonar-Apps (Basis-Theme, Provider-Settings,
// Update-Erkennung). Liegt als Geschwister-Repo neben diesem Repository.
include(":common")
project(":common").projectDir = file("../android-apps-common/android")
