/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderDebugConfig
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

class RemotePreferencesDebugSourceTest {

    @Test
    fun readsStoredSwitchFromRemoteGroup() {
        val module = FakeXposedInterface(
            groups = mapOf(
                ProviderDebugConfig.prefsName(ProviderId.SALT) to mapOf(
                    ProviderDebugConfig.KEY_DEBUG_ENABLED to true
                )
            )
        )
        val source = RemotePreferencesDebugSource(module)
        assertEquals(true, source.read(ProviderId.SALT))
    }

    @Test
    fun missingKeyResolvesToDisabledDefault() {
        val module = FakeXposedInterface(
            groups = mapOf(ProviderDebugConfig.prefsName(ProviderId.SALT) to emptyMap())
        )
        val source = RemotePreferencesDebugSource(module)
        assertEquals(false, source.read(ProviderId.SALT))
    }

    @Test
    fun unavailableRemoteCapabilityFailsClosedToNull() {
        val module = FakeXposedInterface(groups = emptyMap(), failGroups = true)
        val source = RemotePreferencesDebugSource(module)
        assertNull(source.read(ProviderId.SALT))
    }
}

private class FakeSharedPreferences(private val values: Map<String, Any?>) : SharedPreferences {
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as? Set<String> ?: defValues)
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("read-only")
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = throw UnsupportedOperationException("read-only")
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = throw UnsupportedOperationException("read-only")
}

private class FakeXposedInterface(
    private val groups: Map<String, Map<String, Any?>>,
    private val failGroups: Boolean = false
) : XposedInterface {

    override fun getFrameworkName(): String = "FakeFramework"
    override fun getFrameworkVersion(): String = "0.0.0"
    override fun getFrameworkVersionCode(): Long = 0
    override fun getFrameworkProperties(): Long = 0
    override fun hook(origin: Executable): XposedInterface.HookBuilder =
        throw UnsupportedOperationException("not needed in config tests")
    override fun hookClassInitializer(origin: Class<*>): XposedInterface.HookBuilder =
        throw UnsupportedOperationException("not needed in config tests")
    override fun deoptimize(executable: Executable): Boolean = false
    override fun getInvoker(method: Method): XposedInterface.Invoker<*, Method> =
        throw UnsupportedOperationException("not needed in config tests")
    override fun <T> getInvoker(constructor: Constructor<T>): XposedInterface.CtorInvoker<T> =
        throw UnsupportedOperationException("not needed in config tests")
    override fun log(priority: Int, tag: String?, msg: String) = Unit
    override fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) = Unit
    override fun getModuleApplicationInfo(): ApplicationInfo = ApplicationInfo()
    override fun getRemotePreferences(group: String): SharedPreferences {
        if (failGroups) throw UnsupportedOperationException("embedded framework")
        val values = groups[group] ?: throw UnsupportedOperationException("group not found")
        return FakeSharedPreferences(values)
    }
    override fun listRemoteFiles(): Array<String> = emptyArray()
    override fun openRemoteFile(name: String): ParcelFileDescriptor =
        throw UnsupportedOperationException("not needed in config tests")
}
