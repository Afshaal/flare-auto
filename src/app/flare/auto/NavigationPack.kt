package app.flare.auto

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The offline navigation pack built by flare-lahore-tools: a GraphHopper graph, a Mapsforge map and a place
 * search database, described by build-info.json and checksums.txt. It is not part of the APK; it is copied to
 *
 *     <external files dir>/navigation/<region id>/      (e.g. /sdcard/Android/data/app.flare.auto/files/navigation/lahore/)
 *
 * Several packs may be installed side by side (e.g. "lahore" and "pakistan"); the driver picks one in Settings ›
 * Offline maps ([select], stored device-wide, not per driver profile). Maps, search, routing and guidance all read
 * the pack through [status] / [verified], so they always use the same one. Without a usable choice (nothing chosen
 * yet, or the chosen folder was removed) the first folder by name that reads as ready is used, else the first by name.
 *
 * [status] is cheap (reads build-info.json, checks the files exist). [verified] also checks every file against
 * checksums.txt the first time and whenever the files change (remembered per pack); call it off the main thread.
 * A missing or broken pack is reported as a [Status], never thrown.
 */
internal object NavigationPack {
    /** Pack layout this app understands (build-info.json "pack_format"). */
    const val FORMAT = 1
    private const val INFO = "build-info.json"
    private const val CHECKSUMS = "checksums.txt"
    private const val STATE = "navigation_pack"
    /** Legacy single-pack verification stamp (apps before pack selection); still honoured. */
    private const val VERIFIED = "verified"
    private const val VERIFIED_PREFIX = "verified:"
    private const val SELECTED = "selected"

    class Pack(
        val dir: File,
        val id: String,
        val region: String,
        /** min lat, min lon, max lat, max lon */
        val bbox: DoubleArray,
        val graph: File,
        val map: File,
        val search: File,
        val checksumsSha256: String,
        /** Display map for the MapLibre renderer (pack revision 3+); null in older packs. */
        val vector: VectorMap? = null
    ) {
        fun covers(lat: Double, lon: Double) =
            lat.isFinite() && lon.isFinite() && lat in bbox[0]..bbox[2] && lon in bbox[1]..bbox[3]
    }

    /** Vector tiles (MBTiles), MapLibre style with a `{pack}` placeholder, and the local glyph folder. */
    class VectorMap(val tiles: File, val style: File, val glyphs: File, val maxZoom: Int)

    sealed class Status {
        object Missing : Status()
        class Invalid(val reason: String) : Status()
        class Ready(val pack: Pack) : Status()

        /** One line for the driver. */
        val message: String
            get() = when (this) {
                Missing -> "Offline navigation pack not installed"
                is Invalid -> "Offline navigation pack unusable: $reason"
                is Ready -> "${pack.region} offline navigation"
            }
    }

    fun root(context: Context): File? = context.getExternalFilesDir(null)?.let { File(it, "navigation") }

    /** Installed pack folders (those with a build-info.json), by name. */
    fun installed(context: Context): List<File> = root(context)?.let(::installedIn) ?: emptyList()

    fun installedIn(root: File): List<File> = root.listFiles()?.filter { File(it, INFO).isFile }?.sortedBy { it.name } ?: emptyList()

    /** Folder name the driver chose, or null. */
    fun selected(context: Context): String? = state(context).getString(SELECTED, null)

    /** Makes [folder] the pack used by maps, search and routing; Home and the map reload on their next resume. */
    fun select(context: Context, folder: String) {
        state(context).edit().putString(SELECTED, folder).apply()
        OfflineMapStore.touch(context)
    }

    /**
     * The pack folder to use among [dirs] (sorted by name): the [selected] one when it is still installed, even if
     * it is broken (its problem is then shown rather than silently using other data), else the first that [ready]
     * accepts, else the first.
     */
    fun choose(dirs: List<File>, selected: String?, ready: (File) -> Boolean): File? =
        dirs.firstOrNull { it.name == selected } ?: dirs.firstOrNull(ready) ?: dirs.firstOrNull()

    /** The selected pack (see [choose]), validated without hashing. */
    fun status(context: Context): Status {
        val root = root(context) ?: return Status.Missing
        val dir = choose(installedIn(root), selected(context)) { read(it) is Status.Ready } ?: return Status.Missing
        return read(dir)
    }

    private fun state(context: Context) = context.applicationContext.getSharedPreferences(STATE, 0)

    /** Like [status], plus a full checksum check (cached until the files change). Blocking: worker thread only. */
    @Synchronized
    fun verified(context: Context): Status {
        val status = status(context)
        if (status !is Status.Ready) return status
        val pack = status.pack
        val stamp = stamp(pack)
        val prefs = state(context)
        val key = VERIFIED_PREFIX + pack.dir.name
        if (prefs.getString(key, null) == stamp || prefs.getString(VERIFIED, null) == stamp) return status
        verifyChecksums(pack.dir, pack.checksumsSha256)?.let { return Status.Invalid(it) }
        prefs.edit().putString(key, stamp).apply()
        return status
    }

    /** Ready pack or null; for callers that only need "is navigation available". Worker thread only. */
    fun readyOrNull(context: Context): Pack? = (verified(context) as? Status.Ready)?.pack

    /** Parses build-info.json in [dir] and checks the files it names exist. */
    fun read(dir: File): Status = try {
        val info = JSONObject(File(dir, INFO).readText())
        val format = info.optInt("pack_format", -1)
        if (format != FORMAT) Status.Invalid("format $format, this app reads format $FORMAT")
        else {
            val box = info.getJSONObject("bbox")
            val files = info.getJSONObject("files")
            // Optional: a pack whose vector files are missing still routes and shows the Mapsforge map.
            val vector = files.optJSONObject("vector")?.let { v ->
                VectorMap(File(dir, v.getString("tiles")), File(dir, v.getString("style")), File(dir, v.getString("glyphs")),
                    v.optInt("maxzoom", 15))
            }?.takeIf { it.tiles.isFile && it.style.isFile && it.glyphs.isDirectory }
            val pack = Pack(dir, info.getString("pack_id"), info.optString("region", dir.name),
                doubleArrayOf(box.getDouble("min_lat"), box.getDouble("min_lon"), box.getDouble("max_lat"), box.getDouble("max_lon")),
                File(dir, files.getString("graph_dir")), File(dir, files.getString("map")), File(dir, files.getString("search")),
                info.getString("checksums_sha256"), vector)
            when {
                !File(pack.graph, "properties").isFile -> Status.Invalid("routing graph missing")
                !pack.map.isFile -> Status.Invalid("map file missing")
                !pack.search.isFile -> Status.Invalid("search database missing")
                !File(dir, CHECKSUMS).isFile -> Status.Invalid("checksums.txt missing")
                pack.bbox[0] >= pack.bbox[2] || pack.bbox[1] >= pack.bbox[3] -> Status.Invalid("bad coverage box")
                else -> Status.Ready(pack)
            }
        }
    } catch (e: Exception) {
        Status.Invalid("build-info.json unreadable (${e.javaClass.simpleName})")
    }

    /** Null when checksums.txt matches its recorded hash and every file it lists; otherwise the problem. */
    fun verifyChecksums(dir: File, checksumsSha256: String): String? {
        val list = File(dir, CHECKSUMS)
        if (sha256(list) != checksumsSha256) return "checksums.txt does not match build-info.json"
        val base = dir.canonicalPath + File.separator
        var count = 0
        for (line in list.readLines()) {
            if (line.isBlank()) continue
            val hash = line.substringBefore("  ")
            val name = line.substringAfter("  ", "")
            if (hash.length != 64 || name.isEmpty()) return "checksums.txt is malformed"
            val file = File(dir, name)
            if (!file.canonicalPath.startsWith(base)) return "checksums.txt names a file outside the pack"
            if (!file.isFile) return "$name missing"
            if (sha256(file) != hash) return "$name is corrupt"
            count++
        }
        return if (count == 0) "checksums.txt lists no files" else null
    }

    private fun stamp(pack: Pack): String {
        val files = pack.dir.walkTopDown().filter { it.isFile }.toList()
        return "${pack.checksumsSha256}|${files.size}|${files.sumOf { it.length() }}|${files.maxOf { it.lastModified() }}"
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
