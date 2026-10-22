package io.github.cn_cizhanyue.lyricon.universal.provider

import android.content.Context
import okhttp3.OkHttp

/** All callers are network workers. Preserve OkHttp's Android context without eager startup. */
internal object UniversalAppNetwork {
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            OkHttp.initialize(context.applicationContext)
            initialized = true
        }
    }
}
