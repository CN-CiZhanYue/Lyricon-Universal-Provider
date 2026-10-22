/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import android.app.Application
import android.os.Build
import java.io.File

internal object ProviderProcessNames {

    fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val appProcessName = Application.getProcessName()
            if (!appProcessName.isNullOrBlank()) return appProcessName
        }
        return runCatching { File("/proc/self/cmdline").readText().trim('\u0000', ' ') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }
}
