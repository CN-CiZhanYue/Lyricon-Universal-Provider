# Copyright 2026 Andrea-TB
# Licensed under the Apache License, Version 2.0
# http://www.apache.org/licenses/LICENSE-2.0
#
# v4.1 libxposed API 102 unified release rules. Referenced by every Provider app module that has
# completed the API 102 migration (via gradle/provider-app-convention.gradle.kts).
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
