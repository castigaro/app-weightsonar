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
// Update-Erkennung). Liegt im Monorepo neben apps/.
include(":common")
project(":common").projectDir = file("../../common/android")
