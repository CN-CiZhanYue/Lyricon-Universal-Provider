@file:Suppress("UnstableApiUsage")
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal(); maven { url = uri("https://api.xposed.info/") } } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral(); maven { url = uri("https://api.xposed.info/") }; maven { url = uri("https://jitpack.io") } } }
rootProject.name = "LyriconUniversalProvider"
include(":universal-provider-lyricon", ":provider-core", ":provider-hook-api102", ":parser-lrc", ":parser-qrc", ":parser-yrc")
