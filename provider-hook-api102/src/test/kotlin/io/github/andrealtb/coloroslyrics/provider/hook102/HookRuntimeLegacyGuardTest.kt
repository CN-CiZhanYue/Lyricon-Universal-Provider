/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The modern runtime must never reference legacy Xposed or Yuki symbols. This scan is a migration
 * guard only; the final forbidden scan runs against every runtime source and the APK itself.
 */
class HookRuntimeLegacyGuardTest {

    private val forbidden = listOf(
        "de.robv.android.xposed",
        "XSharedPreferences",
        "XposedBridge",
        "XposedHelpers",
        "XC_MethodHook",
        "com.highcapable.yukihookapi",
        "IYukiHookXposedInit",
        "YukiBaseHooker",
        "InjectYukiHookWithXposed",
        "MODE_WORLD_READABLE",
        "xposedsharedprefs",
        "xposedminversion"
    )

    @Test
    fun mainSourcesAreLegacyFree() {
        val sourceRoot = File("src/main")
        assertTrue("expected src/main under module directory", sourceRoot.isDirectory)
        val offenders = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .forEach { file ->
                val content = file.readText()
                forbidden.forEach { token ->
                    if (content.contains(token)) {
                        offenders.add("${file.path}: $token")
                    }
                }
            }
        assertTrue("legacy references found: $offenders", offenders.isEmpty())
    }
}
