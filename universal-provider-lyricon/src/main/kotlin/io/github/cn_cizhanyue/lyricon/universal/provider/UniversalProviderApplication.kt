package io.github.cn_cizhanyue.lyricon.universal.provider

import android.app.Application
import android.content.Context
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.cn_cizhanyue.lyricon.universal.provider.engine.UniversalLyricEngine
import io.github.cn_cizhanyue.lyricon.universal.provider.session.SessionRepository

class UniversalProviderApplication : Application() {
    val repository: SessionRepository by lazy { SessionRepository() }
    val lyricEngine: UniversalLyricEngine by lazy { UniversalLyricEngine(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        UniversalAppIo.execute {
            StructuredDiagnostics.configure(
                debugEnabled = getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(UniversalSettingsConstants.KEY_DEBUG, false)
            )
        }
    }

    companion object {
        @Volatile
        private var instance: UniversalProviderApplication? = null

        fun repository(): SessionRepository =
            instance?.repository ?: error("UniversalProviderApplication is not created")

        fun lyricEngine(context: Context): UniversalLyricEngine =
            instance?.lyricEngine ?: UniversalLyricEngine(context.applicationContext)
    }
}
