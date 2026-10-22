package io.github.cn_cizhanyue.lyricon.universal.provider

import android.os.Process
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** App-owned disk work is ordered and outlives an individual Compose screen. */
internal object UniversalAppIo {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "UniversalAppIo").apply { isDaemon = true }
    }
    val dispatcher = executor.asCoroutineDispatcher()

    fun execute(task: () -> Unit) {
        executor.execute { task() }
    }
}
