@file:Suppress("UnstableApiUsage")

rootProject.name = "tricky"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tricky"

include(":client", ":server")
