package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The pack contract with flare-lahore-tools (build-info.json + checksums.txt) and graceful failure. */
class NavigationPackTest {
    @get:Rule val temp = TemporaryFolder()

    private fun pack(format: Int = 1, mutate: (File) -> Unit = {}): File {
        val dir = temp.newFolder("lahore")
        File(dir, "gh").mkdirs()
        File(dir, "gh/properties").writeText("graph")
        File(dir, "gh/edges").writeText("edges")
        File(dir, "lahore.map").writeText("map")
        File(dir, "search.sqlite").writeText("db")
        val lines = listOf("gh/edges", "gh/properties", "lahore.map", "search.sqlite")
            .joinToString("\n", postfix = "\n") { "${NavigationPack.sha256(File(dir, it))}  $it" }
        File(dir, "checksums.txt").writeText(lines)
        File(dir, "build-info.json").writeText("""{"pack_format":$format,"pack_id":"lahore-test","region":"Lahore",
            "bbox":{"min_lat":31.05,"min_lon":73.9,"max_lat":31.85,"max_lon":74.8},
            "files":{"graph_dir":"gh","map":"lahore.map","search":"search.sqlite"},
            "checksums_sha256":"${NavigationPack.sha256(File(dir, "checksums.txt"))}"}""")
        mutate(dir)
        return dir
    }

    @Test
    fun validPackIsReadyAndVerifies() {
        val dir = pack()
        val status = NavigationPack.read(dir)
        assertTrue(status.message, status is NavigationPack.Status.Ready)
        val ready = (status as NavigationPack.Status.Ready).pack
        assertEquals("Lahore", ready.region)
        assertTrue(ready.covers(31.52, 74.35))
        assertTrue(!ready.covers(33.68, 73.05))
        assertTrue(!ready.covers(Double.NaN, 74.35))
        assertNull(NavigationPack.verifyChecksums(dir, ready.checksumsSha256))
    }

    @Test
    fun optionalBuildingOverlayDoesNotBreakOldPacks() {
        val dir = pack()
        val folder = File(dir, "vector").apply { mkdirs() }
        File(folder, "map.mbtiles").writeText("test")
        File(folder, "style.json").writeText("{}")
        File(folder, "glyphs").mkdirs()
        val infoFile = File(dir,"build-info.json")
        val info = org.json.JSONObject(infoFile.readText())
        info.getJSONObject("files").put("vector",org.json.JSONObject("""{"tiles":"vector/map.mbtiles",
            "style":"vector/style.json","glyphs":"vector/glyphs","buildings":"vector/buildings.mbtiles"}"""))
        infoFile.writeText(info.toString())
        assertNull((NavigationPack.read(dir) as NavigationPack.Status.Ready).pack.vector!!.buildings)
        File(folder,"buildings.mbtiles").writeText("test")
        assertTrue((NavigationPack.read(dir) as NavigationPack.Status.Ready).pack.vector!!.buildings!!.isFile)
    }

    @Test
    fun compositeSearchAttributionComesFromTheManifest() {
        val dir = pack()
        assertNull((NavigationPack.read(dir) as NavigationPack.Status.Ready).pack.searchCredit)
        val infoFile = File(dir, "build-info.json")
        val info = org.json.JSONObject(infoFile.readText())
        info.put("search_sources", org.json.JSONObject().put("attribution", "OpenStreetMap contributors; Overture Maps contributors"))
        infoFile.writeText(info.toString())
        assertEquals("OpenStreetMap contributors; Overture Maps contributors",
            (NavigationPack.read(dir) as NavigationPack.Status.Ready).pack.searchCredit)
    }

    @Test
    fun unsupportedFormatIsRejected() {
        val status = NavigationPack.read(pack(format = 2))
        assertTrue(status is NavigationPack.Status.Invalid)
        assertTrue(status.message, status.message.contains("format 2"))
    }

    @Test
    fun missingFilesAreReported() {
        val status = NavigationPack.read(pack { File(it, "lahore.map").delete() })
        assertEquals("Offline navigation pack unusable: map file missing", status.message)
    }

    @Test
    fun unreadableInfoIsReportedNotThrown() {
        val status = NavigationPack.read(pack { File(it, "build-info.json").writeText("{not json") })
        assertTrue(status.message, status is NavigationPack.Status.Invalid)
    }

    @Test
    fun corruptionIsDetected() {
        val dir = pack()
        val sum = (NavigationPack.read(dir) as NavigationPack.Status.Ready).pack.checksumsSha256
        File(dir, "gh/edges").writeText("tampered")
        assertEquals("gh/edges is corrupt", NavigationPack.verifyChecksums(dir, sum))
        assertEquals("checksums.txt does not match build-info.json", NavigationPack.verifyChecksums(dir, "0".repeat(64)))
    }

    @Test
    fun checksumListCannotPointOutsideThePack() {
        val dir = pack()
        File(temp.root, "outside.txt").writeText("x")
        File(dir, "checksums.txt").writeText("${NavigationPack.sha256(File(temp.root, "outside.txt"))}  ../outside.txt\n")
        val sum = NavigationPack.sha256(File(dir, "checksums.txt"))
        assertEquals("checksums.txt names a file outside the pack", NavigationPack.verifyChecksums(dir, sum))
    }
}
