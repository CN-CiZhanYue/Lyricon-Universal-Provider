package io.github.cn_cizhanyue.lyricon.universal.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != UniversalSnapshotStore.ACTION_SNAPSHOT_UPDATE &&
            action != UniversalSnapshotStore.ACTION_REQUEST_PLAYER_BINDINGS_SYNC) return
        val text = intent.getStringExtra(UniversalSnapshotStore.EXTRA_SNAPSHOT)
        val appContext = context.applicationContext
        val pending = goAsync()
        UniversalAppIo.execute {
            try {
                runCatching {
                    if (action == UniversalSnapshotStore.ACTION_SNAPSHOT_UPDATE) {
                        text?.let(UniversalSnapshotStore::write)
                    } else {
                        UniversalBindingStore.syncToSystem(
                            appContext,
                            UniversalBindingStore.getBoundPackages(appContext)
                        )
                    }
                }
                if (action == UniversalSnapshotStore.ACTION_SNAPSHOT_UPDATE && text != null) {
                    // The home screen is paused while stopped; record here so rows reach their final status.
                    runCatching { UniversalLyricHistory.record(appContext, UniversalSnapshotUi.parse(text)) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
