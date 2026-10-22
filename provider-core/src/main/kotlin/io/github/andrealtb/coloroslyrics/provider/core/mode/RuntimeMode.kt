/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.mode

enum class RuntimeMode {
    ROOT_MODULE,
    UNKNOWN;

    val isSupported: Boolean
        get() = this == ROOT_MODULE
}
