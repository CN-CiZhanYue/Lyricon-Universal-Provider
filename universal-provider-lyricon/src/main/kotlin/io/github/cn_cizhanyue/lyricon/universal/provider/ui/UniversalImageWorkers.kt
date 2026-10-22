package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.os.Process
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** Package resources and bitmaps should not compete with the render thread at normal priority. */
internal object UniversalImageWorkers {
    val dispatcher = Executors.newFixedThreadPool(2) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "UniversalImageWorker").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
