package app.flare.auto

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import java.lang.ref.WeakReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Density-independent pixels to pixels, rounded like the framework (resources of this context, so UiScale applies). */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
internal fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()

/** Full-screen immersive mode with sticky system bars. */
internal fun Window.immersive() {
    if (Build.VERSION.SDK_INT >= 30) {
        insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}

/**
 * The one background pool for short disk and package-manager work (app lists, icons, images, catalogs).
 * Two threads, idle threads exit after 30 s. Long transfers and the router keep their own single threads.
 */
internal object Background {
    private val executor = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "flare-background").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }
    private val main = Handler(Looper.getMainLooper())

    fun execute(work: () -> Unit) = executor.execute(work)

    fun post(block: () -> Unit) { main.post(block) }

    /** Runs [work] off the main thread; [deliver] runs on the main thread only if [owner] is still alive. */
    fun <T> load(owner: Activity, work: () -> T, deliver: (T) -> Unit) {
        val ref = WeakReference(owner)
        executor.execute {
            val result = work()
            main.post { ref.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.let { deliver(result) } }
        }
    }
}
