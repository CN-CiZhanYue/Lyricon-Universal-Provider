@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://api.xposed.info/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "LyriconUniversalProvider"

include(":universal-provider-lyricon")
include(":provider-core")
include(":provider-hook-api102")
include(":parser-lrc")
include(":parser-qrc")
include(":parser-yrc")
