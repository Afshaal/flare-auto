package app.flare.auto

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object CustomImages {
    private val iconRolePattern = Regex("icon_[0-9a-f]{64}")
    @JvmField
    val cache = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    const val PICK = 912

    @JvmStatic
    fun choose(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            activity.startActivityForResult(intent, PICK)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "No image picker is available", Toast.LENGTH_LONG).show()
        }
    }

    @JvmStatic
    fun file(context: Context, role: String): File {
        require(role == "home" || role == "drawer" || role == "saverwall" || role == "screen" ||
            role.matches(iconRolePattern)
        ) { "image role" }
        if(context is EditorContext) context.images?.let { java.io.File(it,if(role=="home") context.draft.getString("_editor_image","home") ?: "home" else role).takeIf { image->image.isFile }?.let { image->return image } }
        val id = ProfileStore.active(context)
        return File(context.filesDir, if (id == "default") "wallpaper_$role" else "${id}_wallpaper_$role")
    }

    @JvmStatic
    fun importImage(activity: Activity, uri: Uri, role: String, success: Runnable) = importTo(activity,uri,role,file(activity,role),success)

    fun importTo(activity:Activity,uri:Uri,role:String,destination:File,success:Runnable) {
        val context = activity.applicationContext
        val main = Handler(Looper.getMainLooper())
        val target = destination
        target.parentFile?.mkdirs()
        val driver = ProfileStore.active(context)
        Thread {
            var temp = try {
                File.createTempFile("wallpaper_$role", ".tmp", context.filesDir)
            } catch (_: IOException) {
                null
            }
            var succeeded = false
            try {
                val temporaryFile = temp ?: throw IOException("temporary file unavailable")
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("empty image")
                input.use { source ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(16384)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 32L * 1024 * 1024) throw IOException("image too large")
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
            } catch (_: Exception) {
                temp?.delete()
                temp = null
            }
            if (temp != null && temp.isFile) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    if (bounds.outWidth < 1 || bounds.outHeight < 1) throw IOException("invalid image")
                    synchronized(cache) {
                        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        val prefix = "${target.absolutePath}:"
                        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
                    }
                    succeeded = true
                } catch (_: Exception) {
                    temp.delete()
                }
            }
            val done = succeeded
            main.post {
                if (activity.isFinishing || activity.isDestroyed || ProfileStore.active(context) != driver) return@post
                if (done) {
                    success.run()
                } else {
                    Toast.makeText(activity, "Could not import image (maximum 32 MB)", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @JvmStatic
    fun iconRole(packageName: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(packageName.toByteArray(Charsets.UTF_8))
            buildString(69) {
                append("icon_")
                val hex = "0123456789abcdef"
                for (value in digest) {
                    val byte = value.toInt() and 255
                    append(hex[byte ushr 4]); append(hex[byte and 15])
                }
            }
        } catch (exception: Exception) {
            throw IllegalStateException(exception)
        }
    }

    @JvmStatic
    fun load(context: Context, role: String): Drawable? {
        val target = file(context, role)
        if (!target.isFile) return null
        val key = cacheKey(context, role, target)
        synchronized(cache) { cache.get(key)?.let { return Crop(it) } }
        return if (Looper.myLooper() == Looper.getMainLooper()) AsyncImages.load(context) { app ->
            synchronized(cache) { loadCached(app, role, target) }
        } else synchronized(cache) { loadCached(context, role, target) }
    }

    private fun limit(context: Context, role: String): Int = if (role.startsWith("icon_")) 256 else
        max(1, min(max(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels), 2048))

    private fun cacheKey(context: Context, role: String, file: File): String =
        "${file.absolutePath}:${file.lastModified()}:${file.length()}:${limit(context, role)}"

    @JvmStatic
    fun loadCached(context: Context, role: String, target:File?=null): Drawable? {
        val file = target ?: file(context, role)
        if (!file.isFile) return null
        val limit = limit(context, role)
        val key = cacheKey(context, role, file)
        cache.get(key)?.let { return Crop(it) }
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth < 1 || options.outHeight < 1) return null
            var sample = 1
            while (max(options.outWidth, options.outHeight) / sample > limit) sample *= 2
            options.inSampleSize = sample
            options.inJustDecodeBounds = false
            options.inPreferredConfig = if (role.startsWith("icon_")) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) cache.put(key, bitmap)
            bitmap?.let { Crop(it) }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    class Crop(@JvmField val bitmap: Bitmap) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val source = Rect()

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            val scale = max(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
            val sourceWidth = bounds.width() / scale
            val sourceHeight = bounds.height() / scale
            val left = max(0, ((bitmap.width - sourceWidth) / 2).roundToInt())
            val top = max(0, ((bitmap.height - sourceHeight) / 2).roundToInt())
            source.set(
                left,
                top,
                min(bitmap.width, (left + sourceWidth).roundToInt()),
                min(bitmap.height, (top + sourceHeight).roundToInt())
            )
            canvas.drawBitmap(bitmap, source, bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
