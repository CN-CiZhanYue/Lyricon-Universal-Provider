package io.github.cn_cizhanyue.lyricon.universal.provider

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.CustomBackgroundStore
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.ThemeStore

class CustomBackgroundActivity : ComponentActivity() {

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        val previous = ThemeStore.settings.customBackground
        Thread {
            val path = CustomBackgroundStore.import(this, uri, previous)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (path != null) {
                        ThemeStore.update(this, ThemeStore.settings.copy(customBackground = path))
                    }
                    finish()
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.load(this)
        if (savedInstanceState == null) {
            runCatching { picker.launch("image/*") }.onFailure { finish() }
        } else {
            finish()
        }
    }
}
