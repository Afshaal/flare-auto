package app.flare.auto

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Download map pack": lists the navigation packs linked from the project README on GitHub and downloads the chosen
 * one, which [PackImport] then extracts and verifies exactly like a ZIP picked from USB.
 *
 * Only on request (no background checks). The list is the README's markdown links to `.zip` release assets of this
 * repository ([links]), so adding a pack to the README makes it appear here; any other link is ignored. The download
 * goes to the app's external cache (same storage as the packs) and is deleted after the import.
 */
internal object MapPackDownload {
    const val README = "https://raw.githubusercontent.com/Afshaal/flare-auto/main/README.md"
    private const val ASSETS = "/Afshaal/flare-auto/releases/download/"
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    private const val RESERVE_BYTES = 64L * 1024 * 1024
    private const val RETRIES = 4
    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile var busy = false; private set
    @Volatile private var cancelled = false

    data class Link(val name: String, val url: String)

    /** Markdown links `[name](url)` in [readme] that point at a `.zip` release asset of this repository. */
    fun links(readme: String): List<Link> =
        Regex("""\[([^\]\n]+)]\((https://[^)\s]+)\)""").findAll(readme).mapNotNull { m ->
            val url = m.groupValues[2]
            val uri = runCatching { URL(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.protocol == "https" && uri.host.equals("github.com", true) && uri.path.startsWith(ASSETS) &&
                uri.path.endsWith(".zip", true) && !uri.path.contains("..")) Link(m.groupValues[1].trim(), url) else null
        }.distinctBy { it.url }.toList()

    fun cancel() { cancelled = true }

    /** Fetches the README and lists its packs; [done] runs on the main thread (null list = failed, with a reason). */
    fun list(done: (List<Link>?, String) -> Unit) {
        Thread {
            val result = runCatching {
                val conn = open(README)
                try {
                    if (conn.responseCode != 200) error("GitHub returned HTTP ${conn.responseCode}")
                    conn.inputStream.bufferedReader().use { r ->
                        val text = StringBuilder(); val buf = CharArray(8192)
                        while (text.length <= 500_000) { val n = r.read(buf); if (n < 0) break; text.append(buf, 0, n) }
                        text.toString()
                    }
                } finally { conn.disconnect() }
            }
            main.post { result.fold({ done(links(it), "") }, { done(null, it.message ?: it.javaClass.simpleName) }) }
        }.start()
    }

    /**
     * Downloads [link] and hands it to [PackImport]. [progress] gets status lines; [done] the import result. All
     * callbacks run on the main thread.
     *
     * Resumable: the partial file (`map-pack-<hash>.part`, with its URL, size and ETag in a `.meta` file beside it)
     * is kept when the connection drops or the download is cancelled, and the next attempt asks only for the rest
     * (HTTP Range, with If-Range so a changed file on GitHub starts over instead of mixing versions). Dropped
     * connections are also retried a few times right away.
     */
    fun download(c: Context, link: Link, progress: (String) -> Unit, done: (Boolean, String) -> Unit) {
        if (busy || PackImport.busy) return
        val app = c.applicationContext
        busy = true; cancelled = false
        Thread {
            val dir = app.externalCacheDir ?: app.cacheDir
            val key = java.security.MessageDigest.getInstance("SHA-1").digest(link.url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(12)
            val part = File(dir, "map-pack-$key.part")
            val meta = File(dir, "map-pack-$key.meta")
            val file = File(dir, "map-pack-$key.zip")
            val result = runCatching {
                var attempt = 0
                while (true) {
                    try { fetch(link, part, meta, dir, progress); break }
                    catch (e: java.io.IOException) {
                        if (cancelled || ++attempt >= RETRIES) throw e
                        val wait = 2000L * attempt
                        main.post { progress("Connection lost; resuming in ${wait / 1000} s…") }
                        Thread.sleep(wait)
                    }
                }
                file.delete()
                check(part.renameTo(file)) { "Cannot finish the download" }
                meta.delete()
            }
            main.post {
                busy = false
                result.onFailure {
                    val kept = part.length()
                    done(false, "Download " + (if (cancelled) "paused" else "failed: ${it.message ?: it.javaClass.simpleName}") +
                        (if (kept > 0) ". ${mb(kept)} kept; downloading again continues from there." else ""))
                }
                result.onSuccess {
                    progress("Downloaded; importing…")
                    PackImport.start(app, Uri.fromFile(file)) { ok, message -> file.delete(); done(ok, message) }
                }
            }
        }.start()
    }

    /** One attempt: continues [part] when [meta] says it belongs to [link], otherwise starts it again. Worker thread. */
    internal fun fetch(link: Link, part: File, meta: File, dir: File, progress: (String) -> Unit,
                       post: (() -> Unit) -> Unit = { main.post(it) }, requireHttps: Boolean = true) {
        val saved = runCatching { meta.readLines() }.getOrNull()
        val sameFile = saved != null && saved.size >= 3 && saved[0] == link.url
        if (!sameFile) { part.delete(); meta.delete() }
        var offset = if (part.isFile) part.length() else 0L
        val etag = if (sameFile) saved!![1] else ""
        val conn = open(link.url)
        try {
            if (offset > 0) {
                conn.setRequestProperty("Range", "bytes=$offset-")
                if (etag.isNotEmpty()) conn.setRequestProperty("If-Range", etag)
            }
            val code = conn.responseCode
            if (requireHttps && conn.url.protocol != "https") error("Download failed (not HTTPS)")
            val total: Long
            when (code) {
                206 -> total = Regex("""/(\d+)$""").find(conn.getHeaderField("Content-Range") ?: "")?.groupValues?.get(1)?.toLong()
                    ?: (offset + conn.contentLengthLong)
                200 -> { offset = 0; total = conn.contentLengthLong; part.delete() } // no resume possible: start over
                416 -> { if (sameFile && saved!![2].toLongOrNull() == offset) return; part.delete(); meta.delete(); error("Download failed (HTTP 416)") }
                else -> error("Download failed (HTTP $code)")
            }
            // GitHub's storage does not always honour If-Range; a different size means the file changed: start over.
            if (code == 206 && sameFile && saved!![2].toLongOrNull() != total) {
                part.delete(); meta.delete(); throw java.io.IOException("The pack changed on GitHub; starting again")
            }
            if (total > MAX_BYTES) error("The pack is too large")
            if (total > 0 && (total - offset) + total + RESERVE_BYTES > dir.usableSpace) error("Not enough free storage (${mb(total * 2 - offset)} needed)")
            meta.writeText(listOf(link.url, conn.getHeaderField("ETag") ?: "", total.toString()).joinToString("\n", postfix = "\n"))
            conn.inputStream.use { input -> java.io.FileOutputStream(part, offset > 0).use { output ->
                val buffer = ByteArray(65536); var size = offset; var shown = -1L
                while (true) {
                    if (cancelled) error("Cancelled")
                    val n = input.read(buffer); if (n < 0) break
                    size += n
                    if (size > MAX_BYTES) error("The pack is too large")
                    output.write(buffer, 0, n)
                    val step = size / (1024 * 1024)
                    if (step != shown) { shown = step; val s = size
                        post { progress("Downloading ${link.name}: ${mb(s)}" + if (total > 0) " of ${mb(total)}" else "") } }
                }
                if (total > 0 && size < total) throw java.io.IOException("The connection closed early")
                if (total > 0 && size > total) { part.delete(); meta.delete(); error("The download was larger than expected") }
            } }
        } finally { conn.disconnect() }
    }

    private fun open(address: String): HttpURLConnection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Flare-Auto-Map-Packs")
    }

    private fun mb(bytes: Long) = "%.1f MB".format(java.util.Locale.US, bytes / 1048576.0)
}
