package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Choosing among several installed packs (Lahore + Pakistan) and the renderer row of the offline map settings. */
class PackSelectionTest {
    @get:Rule val temp = TemporaryFolder()

    private val root by lazy { temp.newFolder("navigation") }

    private fun pack(folder: String, region: String, bbox: String, map: String = "$folder.map"): File {
        val dir = File(root, folder).apply { mkdirs() }
        File(dir, "gh").mkdirs()
        File(dir, "gh/properties").writeText("graph $folder")
        File(dir, map).writeText("map $folder")
        File(dir, "search.sqlite").writeText("db $folder")
        val lines = listOf("gh/properties", map, "search.sqlite")
            .joinToString("\n", postfix = "\n") { "${NavigationPack.sha256(File(dir, it))}  $it" }
        File(dir, "checksums.txt").writeText(lines)
        File(dir, "build-info.json").writeText("""{"pack_format":1,"pack_id":"$folder-test-r1","region":"$region",
            "bbox":$bbox,"files":{"graph_dir":"gh","map":"$map","search":"search.sqlite"},
            "checksums_sha256":"${NavigationPack.sha256(File(dir, "checksums.txt"))}"}""")
        return dir
    }

    private val lahoreBox = """{"min_lat":31.05,"min_lon":73.9,"max_lat":31.85,"max_lon":74.8}"""
    private val pakistanBox = """{"min_lat":23.35,"min_lon":60.8,"max_lat":37.15,"max_lon":77.25}"""
    private fun ready(dir: File) = NavigationPack.read(dir) is NavigationPack.Status.Ready
    private fun chosen(selected: String?) = NavigationPack.choose(NavigationPack.installedIn(root), selected, ::ready)

    @Test
    fun installedListsOnlyPackFoldersByName() {
        pack("pakistan", "Pakistan", pakistanBox)
        pack("lahore", "Lahore", lahoreBox)
        File(root, "notes").mkdirs()
        File(root, "readme.txt").writeText("x")
        assertEquals(listOf("lahore", "pakistan"), NavigationPack.installedIn(root).map { it.name })
    }

    @Test
    fun selectedPackIsUsedWithoutRenamingEither() {
        pack("lahore", "Lahore", lahoreBox)
        pack("pakistan", "Pakistan", pakistanBox)
        assertEquals("lahore", chosen(null)?.name) // nothing chosen yet: previous behaviour (first by name)
        assertEquals("pakistan", chosen("pakistan")?.name)
        assertEquals("lahore", chosen("lahore")?.name)
    }

    @Test
    fun selectedPackSuppliesMapSearchAndGraphFromItsOwnFolder() {
        pack("lahore", "Lahore", lahoreBox)
        val pakistanDir = pack("pakistan", "Pakistan", pakistanBox)
        val pakistan = (NavigationPack.read(chosen("pakistan")!!) as NavigationPack.Status.Ready).pack
        for (file in listOf(pakistan.graph, pakistan.map, pakistan.search)) {
            assertTrue(file.path, file.canonicalPath.startsWith(pakistanDir.canonicalPath + File.separator))
        }
        assertTrue(pakistan.map.readText().endsWith("pakistan"))
        assertTrue(pakistan.search.readText().endsWith("pakistan"))
        assertTrue(File(pakistan.graph, "properties").readText().endsWith("pakistan"))
        assertTrue(pakistan.covers(24.86, 67.0)) // Karachi
        val lahore = (NavigationPack.read(chosen("lahore")!!) as NavigationPack.Status.Ready).pack
        assertFalse(lahore.covers(24.86, 67.0))
        assertTrue(lahore.search.readText().endsWith("lahore"))
    }

    @Test
    fun removedSelectionFallsBackToAReadyPack() {
        pack("lahore", "Lahore", lahoreBox)
        pack("pakistan", "Pakistan", pakistanBox).deleteRecursively().let { assertTrue(it) }
        assertEquals("lahore", chosen("pakistan")?.name)
    }

    @Test
    fun brokenDefaultIsSkippedButABrokenChoiceIsReported() {
        File(pack("lahore", "Lahore", lahoreBox), "gh/properties").delete() // e.g. the file manager skipped gh/
        pack("pakistan", "Pakistan", pakistanBox)
        assertEquals("pakistan", chosen(null)?.name)
        // The driver chose Lahore: report its problem instead of silently routing on other data.
        val status = NavigationPack.read(chosen("lahore")!!)
        assertEquals("Offline navigation pack unusable: routing graph missing", status.message)
    }

    @Test
    fun noPacksMeansNothingChosen() {
        assertNull(chosen("lahore"))
        File(root, "lahore").mkdirs() // folder copied without build-info.json
        assertNull(chosen(null))
    }

    @Test
    fun onlyBrokenPacksStillReportTheFirst() {
        File(pack("lahore", "Lahore", lahoreBox), "search.sqlite").delete()
        assertEquals("Offline navigation pack unusable: search database missing", NavigationPack.read(chosen(null)!!).message)
    }

    @Test
    fun rendererRowShowsTheRendererActuallyUsed() {
        assertEquals("MapLibre · 3D", SettingsValues.mapRenderer(true, null, true))
        assertEquals("MapLibre · 3D", SettingsValues.mapRenderer(true, null, null)) // pack still being checked
        assertEquals("Mapsforge · 2D", SettingsValues.mapRenderer(false, null, true))
        assertEquals("Mapsforge · 2D (3D unsupported)", SettingsValues.mapRenderer(true, "needs OpenGL ES 3.0", true))
        assertEquals("Mapsforge · 2D (no 3D map in pack)", SettingsValues.mapRenderer(true, null, false))
        assertTrue(SettingsValues.vectorInUse(true, null, true))
        assertFalse(SettingsValues.vectorInUse(false, null, true))
        assertFalse(SettingsValues.vectorInUse(true, "not available for this processor", true))
        assertFalse(SettingsValues.vectorInUse(true, null, false))
    }
}
