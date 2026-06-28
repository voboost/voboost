pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "8.7.3"
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("io.github.takahirom.roborazzi") version "1.48.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "voboost"

// Include the voboost-config library as a subproject
include(":voboost-config")
project(":voboost-config").projectDir = file("../voboost-config")

// Include the voboost-components library as a subproject
include(":voboost-components")
project(":voboost-components").projectDir = file("../voboost-components")
