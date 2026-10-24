import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

apply(from = rootProject.file("gradle/provider-app-convention.gradle.kts"))

configure<ApplicationExtension> {
    namespace = "io.github.cn_cizhanyue.lyricon.universal.provider"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        applicationId = "io.github.cn_cizhanyue.lyricon.universal.provider"
        minSdk = 27
        targetSdk = rootProject.extra.get("targetSdkVersion") as Int
        versionCode = 16
        versionName = "1.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "release.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("performance") {
            // Same optimization as release, signed only for local update testing.
            // Keep the applicationId/debug certificate so existing preferences survive.
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            versionNameSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":provider-core"))
    implementation(project(":parser-lrc"))
    implementation(project(":parser-qrc"))
    implementation(project(":parser-yrc"))
    compileOnly(project(":provider-hook-api102"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation("io.github.proify.lyricon:provider:0.1.70")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

tasks.configureEach {
    if (name == "verifyXposedApi102Resources") {
        enabled = false
    }
}
