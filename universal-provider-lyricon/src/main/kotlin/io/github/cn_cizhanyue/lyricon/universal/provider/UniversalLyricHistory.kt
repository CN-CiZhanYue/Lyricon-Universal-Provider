package io.github.cn_cizhanyue.lyricon.universal.provider

import android.content.Context

/**
 * Recent-track rows on the home screen. system_server keeps the authoritative rows, because it
 * observes every track and fetch result while the app may be frozen or not running, and sends
 * them with each snapshot; the app merges them into its stored list so rows also survive a
 * reboot. Source and status are raw snapshot keys, localized only when displayed.
 */
internal object UniversalLyricHistory {
    private const val PREFS_NAME = "universal_ui_history"
    private const val KEY_ORDERED = "ordered"
    const val MAX_ROWS = 5

    /** Snapshot keys that carry system_server's rows, newest first. */
    val snapshotKeys: List<String> = List(MAX_ROWS) { "history$it" }

    data class Entry(
        val title: String,
        val artist: String,
        val source: String,
        val status: String
    )

    fun record(context: Context, snapshot: Map<String, String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_ORDERED, "").orEmpty()
        val updated = update(previous, snapshot) ?: return
        if (updated != previous) prefs.edit().putString(KEY_ORDERED, updated).apply()
    }

    fun load(context: Context): List<Entry> =
        parse(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ORDERED, "").orEmpty())

    /**
     * Returns the stored rows merged with the snapshot: system_server's rows first, then the
     * current track. Null when the snapshot carries neither.
     */
    internal fun update(previous: String, snapshot: Map<String, String>): String? {
        val serverRows = snapshotKeys.mapNotNull { key ->
            snapshot[key]?.takeIf { it.substringBefore('\t').isNotBlank() }
        }
        var rows: String? = null
        if (serverRows.isNotEmpty()) {
            val known = serverRows.mapTo(HashSet()) { it.substringBefore('\t') }
            // A stored row still fetching that system_server no longer tracks can never finish.
            var merged = previous.lines()
                .filter { it.isNotBlank() && (it.substringBefore('\t') in known || !isFetching(it)) }
                .joinToString("\n")
            for (row in serverRows.asReversed()) merged = moveToTop(merged, row)
            rows = merged
        }
        val current = row(
            field(snapshot, "title", "(missing)"),
            field(snapshot, "artist", "(missing)"),
            snapshot["lyricSource"],
            snapshot["lyricStatus"]
        ) ?: return rows
        return moveToTop(rows ?: previous, current)
    }

    /** One stored row, or null without a title. Fields never contain separators. */
    internal fun row(title: String?, artist: String?, source: String?, status: String?): String? {
        val cleanTitle = clean(title)
        if (cleanTitle.isEmpty()) return null
        return listOf(
            cleanTitle,
            clean(artist),
            clean(source?.substringAfterLast('/')),
            clean(status).ifEmpty { "fetching" }
        ).joinToString("\t")
    }

    /** Puts [row] first, replacing the row of the same title. */
    internal fun moveToTop(previous: String, row: String): String {
        val title = row.substringBefore('\t')
        val rows = previous.lines().filter { it.isNotBlank() && it.substringBefore('\t') != title }
        return (listOf(row) + rows).take(MAX_ROWS).joinToString("\n")
    }

    /** Replaces the row of the same title where it stands; a new title goes first. */
    internal fun replaceInPlace(previous: String, row: String): String {
        val title = row.substringBefore('\t')
        val rows = previous.lines().filter { it.isNotBlank() }
        val index = rows.indexOfFirst { it.substringBefore('\t') == title }
        if (index < 0) return moveToTop(previous, row)
        return rows.toMutableList().apply { set(index, row) }.joinToString("\n")
    }

    /** `historyN=row` snapshot lines for system_server's rows. */
    internal fun snapshotLines(rows: String): String = rows.lines()
        .filter { it.isNotBlank() }
        .take(MAX_ROWS)
        .mapIndexed { index, row -> "${snapshotKeys[index]}=$row\n" }
        .joinToString("")

    internal fun parse(raw: String): List<Entry> = raw.lines().filter { it.isNotBlank() }.map { line ->
        val parts = line.split('\t')
        if (parts.size >= 4) {
            Entry(title = parts[0], artist = parts[1], source = parts[2], status = parts[3])
        } else {
            Entry(
                title = parts.getOrElse(0) { "" },
                artist = "",
                source = parts.getOrElse(1) { "—" },
                status = parts.getOrElse(2) { "" }
            )
        }
    }

    /** Includes labels that older builds stored already localized. */
    private val fetchingStatuses = setOf("pending", "fetching", "正在获取", "Fetching")

    private fun isFetching(row: String): Boolean = row.substringAfterLast('\t') in fetchingStatuses

    private fun field(snapshot: Map<String, String>, key: String, missing: String): String =
        snapshot[key].orEmpty().takeUnless { it == missing }.orEmpty()

    private fun clean(value: String?): String =
        value.orEmpty().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
