package app.flare.auto

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.zip.ZipInputStream

/**
 * "Import navigation pack": extracts a pack ZIP chosen with the system document picker (Downloads, USB) into
 * [NavigationPack.root], so no ADB or file manager is needed.
 *
 * The ZIP holds build-info.json at its root, or one folder that does (e.g. `lahore/build-info.json`). It is
 * extracted into a hidden staging folder next to the packs (same storage, so the final move is a rename), validated
 * with the pack's own rules ([NavigationPack.read], manifest paths inside the pack, a plain region id) and every
 * checksum ([NavigationPack.verifyChecksums]). Only then does it replace a pack with the same region id: the old
 * folder is renamed aside, the new one moved in, and the old one deleted; any failure moves the old one back, so a
 * working pack is never lost. The imported pack is selected ([NavigationPack.select], which also refreshes the map
 * and Home) and marked verified so it is not hashed a second time.
 *
 * Rejected: absolute or `..` paths, backslashes, duplicate entries, more than [MAX_ENTRIES] files or [MAX_BYTES]
 * extracted, ambiguous layouts. Free space is checked before and during extraction. Interrupted imports leave only
 * hidden `.import-*` folders, which [recover] cleans up (restoring a replaced pack if the swap was cut short).
 * Work runs on [OfflineRouting.worker], the thread that already does pack I/O and routing.
 */
internal object PackImport {
    private const val MAX_BYTES = 3L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 20_000
    private const val RESERVE_BYTES = 64L * 1024 * 1024
    private const val STAGING = ".import-staging-"
    private const val OLD = ".import-old-"
    private val REGION_ID = Regex("^[a-z0-9][a-z0-9_-]{0,47}$")
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<WeakReference<OfflineMapStore.Listener>>()
    @Volatile var busy = false; private set
    @Volatile private var cancelled = false
    /** Progress or the last result, for the settings row; empty when nothing happened in this process. */
    @Volatile var status = ""; private set

    fun add(l: OfflineMapStore.Listener) { listeners.add(WeakReference(l)) }
    fun remove(l: OfflineMapStore.Listener) { listeners.removeAll { it.get() == null || it.get() === l } }
    private fun notify(value: String) = main.post { status = value; listeners.removeAll { it.get() == null }; listeners.mapNotNull { it.get() }.forEach { it.mapStatusChanged() } }

    fun cancel() { cancelled = true }

    /** Imports [uri]; [done] gets a message for the driver (also left in [status]). */
    fun start(c: Context, uri: Uri, done: (Boolean, String) -> Unit) {
        if (busy) return
        val app = c.applicationContext
        busy = true; cancelled = false; notify("Preparing import…")
        OfflineRouting.worker.execute {
            val result = runCatching { import(app, uri) }
            busy = false
            val ok = result.isSuccess
            val message = result.fold({ "${it.region} pack imported and selected" }, { "Import failed: ${it.message ?: it.javaClass.simpleName}" })
            notify(message)
            main.post { done(ok, message) }
        }
    }

    /** Removes leftovers of an interrupted import; restores a pack whose replacement was cut short. Worker thread. */
    fun recover(c: Context) { if (!busy) cleanUp(c) }

    private fun cleanUp(c: Context) {
        val root = NavigationPack.root(c) ?: return
        root.listFiles()?.forEach { f ->
            when {
                f.name.startsWith(STAGING) -> f.deleteRecursively()
                f.name.startsWith(OLD) -> {
                    val id = f.name.removePrefix(OLD).substringBeforeLast('-')
                    val target = File(root, id)
                    if (REGION_ID.matches(id) && !target.exists()) f.renameTo(target) else f.deleteRecursively()
                }
            }
        }
    }

    private fun import(app: Context, uri: Uri): NavigationPack.Pack {
        val root = NavigationPack.root(app) ?: error("Storage is not available")
        root.mkdirs()
        cleanUp(app)
        val size = app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        if (size > 0 && size + RESERVE_BYTES > root.usableSpace) error("Not enough free storage (${mb(size)} needed)")
        val staging = File(root, STAGING + System.currentTimeMillis())
        try {
            extract(app, uri, staging, size)
            check(!cancelled) { "Cancelled" }
            notify("Checking pack…")
            val packDir = layout(staging)
            val pack = validate(packDir)
            notify("Verifying checksums…")
            NavigationPack.verifyChecksums(pack.dir, pack.checksumsSha256)?.let { error(it) }
            check(!cancelled) { "Cancelled" }
            notify("Installing…")
            val id = regionId(packDir)
            val installed = replace(root, packDir, id)
            val ready = NavigationPack.read(installed) as? NavigationPack.Status.Ready ?: error("Pack could not be read after installing")
            NavigationPack.markVerified(app, ready.pack)
            NavigationPack.select(app, id)
            return ready.pack
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extract(app: Context, uri: Uri, staging: File, size: Long) {
        check(staging.mkdirs()) { "Cannot create a staging folder" }
        val base = staging.canonicalPath + File.separator
        val seen = HashSet<String>()
        var total = 0L; var files = 0
        val input = app.contentResolver.openInputStream(uri) ?: error("Cannot open the file")
        val counted = Counting(input)
        ZipInputStream(counted).use { zip ->
            val buffer = ByteArray(1 shl 16)
            var lastShown = -1L
            while (true) {
                check(!cancelled) { "Cancelled" }
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name.startsWith("__MACOSX/") || name.substringAfterLast('/') == ".DS_Store") continue
                check(name.isNotEmpty() && !name.startsWith("/") && !name.contains('\\') && !name.contains(':')) { "Unsafe path in archive: $name" }
                val parts = name.trimEnd('/').split('/')
                check(parts.none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe path in archive: $name" }
                val key = parts.joinToString("/")
                check(seen.add(key)) { "Duplicate entry in archive: $key" }
                val out = File(staging, key)
                check(out.canonicalPath.startsWith(base)) { "Unsafe path in archive: $name" }
                if (entry.isDirectory) { out.mkdirs(); continue }
                check(++files <= MAX_ENTRIES) { "Archive has too many files" }
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        check(total <= MAX_BYTES) { "Archive expands to more than ${mb(MAX_BYTES)}" }
                        o.write(buffer, 0, n)
                        if (total and 0xFFFFF < n) {
                            check(!cancelled) { "Cancelled" }
                            check(staging.usableSpace > RESERVE_BYTES) { "Storage is full" }
                        }
                    }
                }
                val shown = if (size > 0) counted.count * 100 / size else total / (1024 * 1024)
                if (shown != lastShown) { lastShown = shown; notify(if (size > 0) "Extracting… $shown %" else "Extracting… ${mb(total)}") }
            }
        }
        check(files > 0) { "The archive is empty or not a ZIP file" }
    }

    /** The pack folder: the archive root, or its single top-level folder holding build-info.json. */
    private fun layout(staging: File): File {
        if (File(staging, "build-info.json").isFile) return staging
        val top = staging.listFiles()?.toList() ?: emptyList()
        check(top.size == 1 && top[0].isDirectory && File(top[0], "build-info.json").isFile) {
            "No navigation pack found (build-info.json at the top or in one folder)"
        }
        return top[0]
    }

    private fun validate(dir: File): NavigationPack.Pack {
        val status = NavigationPack.read(dir)
        val pack = (status as? NavigationPack.Status.Ready)?.pack ?: error((status as? NavigationPack.Status.Invalid)?.reason ?: "Not a navigation pack")
        val base = dir.canonicalPath + File.separator
        val named = listOfNotNull(pack.graph, pack.map, pack.search, pack.vector?.tiles, pack.vector?.style, pack.vector?.glyphs, pack.vector?.buildings)
        named.forEach { check(it.canonicalPath.startsWith(base)) { "The pack's manifest points outside the pack" } }
        regionId(dir)
        return pack
    }

    /** Folder name to install under: build-info's region_id (or the archive folder name), a plain lowercase id. */
    private fun regionId(dir: File): String {
        val info = JSONObject(File(dir, "build-info.json").readText())
        val id = info.optString("region_id", "").ifBlank { dir.name }
        check(REGION_ID.matches(id)) { "Invalid region id \"$id\"" }
        return id
    }

    /** Moves [packDir] to root/[id]; an existing pack is put back if anything fails. */
    private fun replace(root: File, packDir: File, id: String): File {
        val target = File(root, id)
        val old = File(root, "$OLD$id-${System.currentTimeMillis()}")
        val hadOld = target.exists()
        if (hadOld) check(target.renameTo(old)) { "Cannot replace the installed $id pack" }
        if (!packDir.renameTo(target)) {
            if (hadOld) old.renameTo(target)
            error("Cannot move the imported pack into place")
        }
        if (hadOld) old.deleteRecursively()
        return target
    }

    private fun mb(bytes: Long) = "%.0f MB".format(java.util.Locale.US, bytes / 1048576.0)

    private class Counting(input: InputStream) : FilterInputStream(input) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }
}
