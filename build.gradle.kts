plugins { alias(libs.plugins.android.application) apply false; alias(libs.plugins.android.library) apply false; alias(libs.plugins.kotlin.serialization) apply false; alias(libs.plugins.jetbrains.kotlin.jvm) apply false; alias(libs.plugins.compose.compiler) apply false }
extra["compileSdkVersion"] = 37
extra["targetSdkVersion"] = 37
