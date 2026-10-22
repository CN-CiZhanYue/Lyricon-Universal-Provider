/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

/**
 * package + process gate evaluated before any business hook is installed. Rejected processes are
 * detached from further lifecycle callbacks. The accepted package set must stay equal to the
 * matrix scopes in release/v5-provider-matrix.json.
 */
interface ProviderProcessPolicy {
    /** All host packages this Provider covers. */
    val packages: Set<String>

    fun accepts(packageName: String, processName: String): Boolean

    /** Short profile label used in structured logs. Must not contain sensitive values. */
    fun describe(packageName: String, processName: String): String = "default"
}

/** Routes only by package name (main-process players, possibly with several host packages). */
class ScopeOnlyProcessPolicy(override val packages: Set<String>) : ProviderProcessPolicy {
    override fun accepts(packageName: String, processName: String): Boolean = packageName in packages

    override fun describe(packageName: String, processName: String): String = "scope-only"
}

/** Accepts only explicitly enumerated full process names. */
class ExplicitProcessPolicy(
    override val packages: Set<String>,
    private val acceptedProcessNames: Set<String>
) : ProviderProcessPolicy {
    override fun accepts(packageName: String, processName: String): Boolean =
        packageName in packages && processName in acceptedProcessNames

    override fun describe(packageName: String, processName: String): String = "explicit"
}
