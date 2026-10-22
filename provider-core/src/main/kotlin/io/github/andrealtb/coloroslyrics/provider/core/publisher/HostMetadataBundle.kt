/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import android.media.MediaMetadata
import android.os.Bundle
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Reflective access to the value bundle a host [MediaMetadata] already carries.
 *
 * The field is private (AOSP name "mBundle"). It is resolved once, preferring that name and falling
 * back to the only non-static Bundle field in the class hierarchy, then cached. Anything ambiguous
 * resolves to null so callers fail open instead of writing into the wrong object.
 */
object HostMetadataBundle {
    private const val BUNDLE_FIELD_NAME = "mBundle"

    @Volatile
    private var bundleField: Field? = null

    @Volatile
    private var resolutionAttempted = false

    fun bundleOf(metadata: MediaMetadata): Bundle? {
        val field = resolve() ?: return null
        return runCatching { field.get(metadata) as? Bundle }.getOrNull()
    }

    private fun resolve(): Field? {
        bundleField?.let { return it }
        if (resolutionAttempted) return null
        synchronized(this) {
            bundleField?.let { return it }
            if (resolutionAttempted) return null
            val resolved = runCatching { resolveBundleField() }.getOrNull()
            if (resolved != null) {
                bundleField = resolved
            }
            resolutionAttempted = true
            return resolved
        }
    }

    private fun resolveBundleField(): Field? {
        val candidates = classHierarchy(MediaMetadata::class.java)
            .flatMap { it.declaredFields.asList() }
            .filter { !Modifier.isStatic(it.modifiers) && it.type == Bundle::class.java }
        val field = candidates.firstOrNull { it.name == BUNDLE_FIELD_NAME }
            ?: candidates.singleOrNull()
            ?: return null
        field.isAccessible = true
        return field
    }

    private fun classHierarchy(type: Class<*>): List<Class<*>> {
        val hierarchy = ArrayList<Class<*>>(4)
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            hierarchy.add(current)
            current = current.superclass
        }
        return hierarchy
    }
}
