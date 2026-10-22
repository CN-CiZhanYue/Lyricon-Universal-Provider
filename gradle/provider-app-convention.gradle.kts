/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

// v4.1 Provider app module unified convention (only applied to app modules that have completed
// the API 102 migration):
// 1. Centrally declare libxposed API 102 compileOnly and service implementation dependencies;
// 2. Uniformly add the release R8 rules in gradle/libxposed-api102.pro;
// 3. Register verifyXposedApi102Resources static verification and wire it before preBuild.
// Verification source of truth is release/v5-provider-matrix.json; scope must not be maintained
// in a second handwritten copy.
//
// Applied scripts do not see AGP classes on their compile classpath, so Android extension access
// below intentionally uses withGroovyBuilder dynamic dispatch instead of typed DSL imports.

import groovy.json.JsonSlurper

val conventionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val conventionLibxposedApi = conventionCatalog.findLibrary("libxposed-api")
    .orElseThrow { IllegalStateException("libs.versions.toml is missing libxposed-api") }
val conventionLibxposedService = conventionCatalog.findLibrary("libxposed-service")
    .orElseThrow { IllegalStateException("libs.versions.toml is missing libxposed-service") }

plugins.withId("com.android.application") {
    extensions.getByName("android").withGroovyBuilder {
        "buildTypes" {
            "release" {
                "proguardFile"(rootProject.file("gradle/libxposed-api102.pro"))
            }
        }
    }
}

dependencies {
    add("compileOnly", conventionLibxposedApi)
    add("implementation", conventionLibxposedService)
}

val conventionModuleDirectory = projectDir
val conventionModuleName = project.name
val conventionMatrixFile = rootProject.file("release/v5-provider-matrix.json")
val conventionForbiddenManifestMetadata = listOf(
    "xposedmodule",
    "xposeddescription",
    "xposedminversion",
    "xposedsharedprefs",
    "xposedscope"
)

tasks.register("verifyXposedApi102Resources") {
    group = "verification"
    description = "Verify META-INF/xposed resources against the v5 provider matrix contract."
    // Configuration cache: capture only plain locals inside the task action. Top-level script
    // properties would drag the script object into the serialized task state.
    val moduleDirectory = conventionModuleDirectory
    val moduleName = conventionModuleName
    val matrixFile = conventionMatrixFile
    val forbiddenMetadata = conventionForbiddenManifestMetadata
    inputs.file(matrixFile)
    doLast {
        val matrix = JsonSlurper().parse(matrixFile) as Map<*, *>
        val providers = (matrix["providers"] as List<*>).filterIsInstance<Map<String, Any>>()
        val entry = providers.firstOrNull { it["module"] == moduleName }
            ?: throw GradleException("Module $moduleName is missing from v5-provider-matrix.json")
        val expectedScopes = (entry["scopes"] as List<*>).map { it.toString() }.toSet()
        val expectedEntryClass = entry["entryClass"]?.toString()
            ?: throw GradleException("Module $moduleName is missing entryClass in v5-provider-matrix.json")

        val xposedDirectory = File(moduleDirectory, "src/main/resources/META-INF/xposed")
        val modulePropFile = File(xposedDirectory, "module.prop")
        val initListFile = File(xposedDirectory, "java_init.list")
        val scopeListFile = File(xposedDirectory, "scope.list")
        listOf(modulePropFile, initListFile, scopeListFile).forEach { required ->
            if (!required.isFile) {
                throw GradleException("Missing modern xposed resource: ${required.path}")
            }
        }

        val moduleProp = modulePropFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val split = line.indexOf('=')
                if (split <= 0) throw GradleException("Invalid module.prop line: $line")
                line.substring(0, split).trim() to line.substring(split + 1).trim()
            }
        val expectedModuleProp = mapOf(
            "minApiVersion" to matrix["xposedMinApiVersion"].toString(),
            "targetApiVersion" to matrix["xposedTargetApiVersion"].toString(),
            "staticScope" to matrix["xposedStaticScope"].toString(),
            "exceptionMode" to matrix["xposedExceptionMode"].toString(),
            "autoHotReload" to matrix["xposedAutoHotReload"].toString()
        )
        if (moduleProp != expectedModuleProp) {
            throw GradleException("module.prop mismatch. expected=$expectedModuleProp actual=$moduleProp")
        }

        val entryClasses = initListFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (entryClasses.size != 1) {
            throw GradleException("java_init.list must contain exactly one entry class, found $entryClasses")
        }
        val entryClass = entryClasses.single()
        if (entryClass != expectedEntryClass) {
            throw GradleException(
                "java_init.list mismatch for $moduleName. expected=$expectedEntryClass actual=$entryClass"
            )
        }
        val entrySimpleName = entryClass.substringAfterLast('.')
        val sourceRoots = listOf("src/main/kotlin", "src/main/java").map { File(moduleDirectory, it) }
        val entrySourceDeclared = sourceRoots.filter { it.isDirectory }.any { root ->
            root.walkTopDown().any { file ->
                file.isFile && (file.extension == "kt" || file.extension == "java") &&
                    file.readText().contains(Regex("(class|interface|object)\\s+$entrySimpleName\\b"))
            }
        }
        if (!entrySourceDeclared) {
            throw GradleException("java_init.list entry $entryClass has no matching source declaration")
        }

        val actualScopes = scopeListFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (actualScopes != expectedScopes) {
            throw GradleException(
                "scope.list mismatch for $moduleName. expected=$expectedScopes actual=$actualScopes"
            )
        }

        val legacyAssets = File(moduleDirectory, "src/main/assets/xposed_init")
        if (legacyAssets.exists()) {
            throw GradleException("Legacy assets/xposed_init must be removed: ${legacyAssets.path}")
        }
        val yukiInit = File(moduleDirectory, "src/main/resources/META-INF/yukihookapi_init")
        if (yukiInit.exists()) {
            throw GradleException("Legacy META-INF/yukihookapi_init must be removed: ${yukiInit.path}")
        }

        val manifestText = File(moduleDirectory, "src/main/AndroidManifest.xml").readText()
        forbiddenMetadata.forEach { name ->
            if (manifestText.contains("android:name=\"$name\"")) {
                throw GradleException("Legacy Xposed meta-data still declared in manifest: $name")
            }
        }
        val configuredApplicationClass = entry["applicationClass"]?.toString()
        if (configuredApplicationClass.isNullOrBlank()) {
            if (!manifestText.contains("ProviderModuleApplication")) {
                throw GradleException("Manifest application must use the shared ProviderModuleApplication")
            }
        } else if (!manifestText.contains(".$configuredApplicationClass")) {
            throw GradleException("Manifest application differs from matrix applicationClass: $configuredApplicationClass")
        }

        logger.lifecycle(
            "[CLL] component=build area=verification event=XPOSED_API102_RESOURCES_OK module=$moduleName entry=$entryClass scopes=$actualScopes"
        )
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("verifyXposedApi102Resources")
}
