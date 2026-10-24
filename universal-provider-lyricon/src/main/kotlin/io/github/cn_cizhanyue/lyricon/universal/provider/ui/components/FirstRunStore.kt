package io.github.cn_cizhanyue.lyricon.universal.provider.ui.components

import android.content.Context

object FirstRunStore {
    private const val PREFS = "lyricon_first_run"
    private const val KEY_ABOUT = "about_shown"

    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ABOUT, false)

    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ABOUT, true).apply()
    }
}
