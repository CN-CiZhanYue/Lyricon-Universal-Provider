package io.github.cn_cizhanyue.lyricon.universal.provider

import android.content.Context
import android.content.Intent
import android.os.UserHandle

internal object UniversalBridgeBindingNotifier {
    const val ACTION = "io.github.andrealtb.universallyrics.action.PLAYER_BINDINGS_CHANGED"
    const val EXTRA_BOUND_PACKAGES = UniversalSnapshotStore.EXTRA_BOUND_PACKAGES
    const val EXTRA_PROVIDER_IDENTITY = "extra_provider_identity"
    const val PROVIDER_IDENTITY = "universal-player-provider"

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val BRIDGE_PACKAGE = "io.github.andrealtb.lockscreenlyrics"
    private const val FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000

    fun boundPackageList(packages: Set<String>): ArrayList<String> {
        return ArrayList(PlayerBindingPolicy.sanitize(packages))
    }

    fun sendSafely(context: Context, intent: Intent): Result<Unit> {
        val allUser = runCatching {
            Class.forName("android.os.UserHandle").getDeclaredField("ALL").get(null) as? UserHandle
        }.getOrNull()
        val isSystemUid = android.os.Process.myUid() == android.os.Process.SYSTEM_UID
        if (isSystemUid && allUser != null) {
            val asUser = runCatching { context.sendBroadcastAsUser(intent, allUser) }
            if (asUser.isSuccess) {
                return asUser
            }
        }
        return runCatching { context.sendBroadcast(intent) }
    }

    fun notify(context: Context, packages: Set<String>) {
        val extras = boundPackageList(packages)
        for (target in arrayOf(SYSTEM_UI_PACKAGE, BRIDGE_PACKAGE)) {
            val intent = Intent(ACTION).apply {
                setPackage(target)
                putStringArrayListExtra(EXTRA_BOUND_PACKAGES, extras)
                putExtra(EXTRA_PROVIDER_IDENTITY, PROVIDER_IDENTITY)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND)
            }
            val sent = sendSafely(context, intent)
            UniversalDiagnostics.bridgeBindingsNotified(
                extras.size,
                target,
                sent.isSuccess,
                sent.exceptionOrNull()?.javaClass?.simpleName
            )
        }
    }
}
