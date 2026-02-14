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

rootProject.name = "voboost"

// Include the voboost-config library as a subproject
include(":voboost-config")
project(":voboost-config").projectDir = file("../voboost-config")

// Include the voboost-components library as a subproject
include(":voboost-components")
project(":voboost-components").projectDir = file("../voboost-components")
