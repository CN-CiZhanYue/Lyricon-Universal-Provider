/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import android.app.Application
import android.content.Context
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticSink
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hooks Application.attach(Context) after onPackageReady and performs the one-time business
 * bootstrap inside the process. Guards repeated package callbacks, repeated attach invocations
 * and installer failures without affecting the host process.
 */
internal class ProviderApplicationBootstrap(
    private val entry: XposedModule,
    private val runtime: ProviderHookRuntime,
    private val providerId: ProviderId,
    private val diagnosticsComponent: String,
    private val frameworkSink: DiagnosticSink,
    private val installer: ProviderHookInstaller
) {
    private val attachHookScheduled = AtomicBoolean(false)
    private val hostBootstrapped = AtomicBoolean(false)
    private val lock = Any()

    fun schedule(param: XposedModuleInterface.PackageReadyParam, processName: String) {
        if (!attachHookScheduled.compareAndSet(false, true)) return
        try {
            val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
            val installed = runtime.hook(attach, "app.attach") {
                after {
                    // Application.attach(Context) receives the base context as arg0, while
                    // business installers that own application-scoped workers need the actual
                    // Application instance. Yuki's old onCreate lifecycle supplied that
                    // instance; preserve the same contract after the API 102 migration.
                    val hostApplication = instanceOrNull as? Application ?: return@after
                    bootstrapHost(hostApplication, param, processName)
                }
            }
            if (!installed) {
                StructuredDiagnostics.logWarning(
                    DiagnosticEvent(
                        component = diagnosticsComponent,
                        area = "bootstrap",
                        event = "HOOK_INSTALL_FAILED",
                        process = processName,
                        reason = "duplicate app.attach registration"
                    )
                )
            }
        } catch (error: Throwable) {
            StructuredDiagnostics.logError(
                DiagnosticEvent(
                    component = diagnosticsComponent,
                    area = "bootstrap",
                    event = "HOOK_INSTALL_FAILED",
                    process = processName,
                    reason = error.javaClass.simpleName,
                    message = error.message?.take(1000)
                ),
                throwable = error
            )
        }
    }

    private fun bootstrapHost(
        hostContext: Context,
        param: XposedModuleInterface.PackageReadyParam,
        processName: String
    ) {
        if (!hostBootstrapped.compareAndSet(false, true)) return
        synchronized(lock) {
            val hostVersion = runCatching {
                hostContext.packageManager.getPackageInfo(hostContext.packageName, 0).versionName
            }.getOrNull()
            val hookContext = ProviderHookContext(
                providerId = providerId,
                packageName = param.packageName,
                processName = processName,
                classLoader = param.classLoader,
                application = hostContext,
                hostVersion = hostVersion,
                runtime = runtime,
                debugSource = RemotePreferencesDebugSource(entry),
                frameworkSink = frameworkSink
            )
            runCatching {
                installer.install(hookContext)
            }.onFailure { error ->
                StructuredDiagnostics.logError(
                    DiagnosticEvent(
                        component = diagnosticsComponent,
                        area = "bootstrap",
                        event = "HOOK_INSTALL_FAILED",
                        process = processName,
                        reason = error.javaClass.simpleName,
                        message = error.message?.take(1000)
                    ),
                    throwable = error
                )
            }
            StructuredDiagnostics.logInfo(
                DiagnosticEvent(
                    component = diagnosticsComponent,
                    area = "bootstrap",
                    event = "HOOK_INSTALL_SUMMARY",
                    process = processName,
                    reason = "hooks=${runtime.installedHookCount} package=${param.packageName}"
                )
            )
        }
    }
}
