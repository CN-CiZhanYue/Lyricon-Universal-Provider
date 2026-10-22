/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.model

enum class LyricTimingType(val protocolValue: Int) {
    INVALID(-1),
    UNTIMED_TEXT(0),
    LINE(1),
    WORD(2);

    companion object {
        fun fromProtocolValue(value: Int): LyricTimingType =
            entries.find { it.protocolValue == value } ?: INVALID
    }
}
