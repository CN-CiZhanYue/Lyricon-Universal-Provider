package io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller

import android.content.Context
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.InstalledAppCatalog
import io.github.cn_cizhanyue.lyricon.universal.provider.PlayerBindingPolicy
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalBindingStore
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.AppItemUi
import kotlinx.coroutines.withContext

class PlayerBindingController(private val context: Context) {

    data class Data(val apps: List<AppItemUi>, val bound: Set<String>)

    suspend fun load(): Data = withContext(UniversalAppIo.dispatcher) {
        val bound = UniversalBindingStore.getBoundPackages(context)
        val apps = InstalledAppCatalog.load(context)
        Data(apps.filter { !PlayerBindingPolicy.isBlocked(it.packageName) }, bound)
    }

    fun toggle(packageName: String, bound: Set<String>, enabled: Boolean): Set<String> {
        val updated = if (enabled) bound + packageName else bound - packageName
        UniversalBindingStore.saveBoundPackages(context, updated)
        return updated
    }
}
