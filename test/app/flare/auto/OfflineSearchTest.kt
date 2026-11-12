package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline search: name normalization, matching, ranking, duplicate grouping and result labels. */
class OfflineSearchTest {
    private val liberty = 31.5102 to 74.3441

    @Test
    fun normalizationJoinsDotsAndApostrophes() {
        assertEquals("gor", OfflineSearch.normalize("G.O.R."))
        assertEquals("gor i", OfflineSearch.normalize("G.O.R. I"))
        assertEquals("mcdonalds", OfflineSearch.normalize("McDonald’s"))
        assertEquals("mcdonalds", OfflineSearch.normalize("McDonald's"))
        assertEquals("model town", OfflineSearch.normalize("Model-Town"))
        assertEquals("dha phase 5", OfflineSearch.normalize("  D.H.A,  Phase (5) "))
        assertEquals("cafe", OfflineSearch.normalize("Café"))
        assertEquals("", OfflineSearch.normalize("..."))
        // Urdu letters survive (diacritics dropped, as in build_search.py).
        assertEquals("لبرٹی", OfflineSearch.normalize("لبرٹی"))
    }

    @Test
    fun partialNamesAndPunctuationMatch() {
        val gor = OfflineSearch.normalize("G.O.R.")
        assertEquals(0, OfflineSearch.score(gor, OfflineSearch.normalize("gor")))
        assertEquals(1, OfflineSearch.score(OfflineSearch.normalize("Liberty Market"), "lib"))
        assertEquals(2, OfflineSearch.score("liberty market parking", "liberty park"))
        assertEquals(3, OfflineSearch.score("market liberty", "liberty mar"))
        assertEquals(4, OfflineSearch.score("gulberg 3", "gulberg3"))
        assertEquals(5, OfflineSearch.score("something else", "xyz"))
        assertEquals("\"gor\"* \"i\"*", OfflineSearch.matchTerms(listOf("gor", "i")))
        assertEquals("\"a\"\"b\"*", OfflineSearch.matchTerms(listOf("a\"b", "...")))
    }

    @Test
    fun exactMatchesFirstThenNearest() {
        val far = OfflineSearch.Hit("Gor Gulab", "village", 31.2, 74.0)
        val exactFar = OfflineSearch.Hit("G.O.R.", "suburb", 31.56, 74.33)
        val near = OfflineSearch.Hit("GOR 5", "place", 31.5105, 74.3445)
        val ranked = OfflineSearch.rank(listOf(far, near, exactFar), "gor", liberty.first, liberty.second)
        assertEquals(listOf("G.O.R.", "GOR 5", "Gor Gulab"), ranked.map { it.hit.name })
        // Same text quality: nearer first.
        val a = OfflineSearch.Hit("Bank Alfalah", "bank", 31.60, 74.40)
        val b = OfflineSearch.Hit("Bank Alfalah", "bank", 31.511, 74.345)
        assertEquals(listOf(b, a), OfflineSearch.rank(listOf(a, b), "bank alfalah", liberty.first, liberty.second).map { it.hit })
    }

    @Test
    fun trueDuplicatesAreGroupedDistinctPlacesKept() {
        val en = OfflineSearch.Hit("Liberty Market", "marketplace", 31.5102, 74.3441, osm = "n1")
        val ur = OfflineSearch.Hit("لبرٹی مارکیٹ", "marketplace", 31.5102, 74.3441, osm = "n1")
        val street1 = OfflineSearch.Hit("Liberty Market", "street", 31.5110, 74.3450, osm = "w1")
        val street2 = OfflineSearch.Hit("Liberty Market", "street", 31.5120, 74.3460, osm = "w2")
        val otherBranch = OfflineSearch.Hit("Liberty Books", "books", 31.47, 74.27, osm = "n2")
        val sameNameElsewhere = OfflineSearch.Hit("Liberty Books", "books", 31.52, 74.35, osm = "n3")
        val ranked = OfflineSearch.rank(listOf(en, ur, street1, street2, otherBranch, sameNameElsewhere), "liberty", liberty.first, liberty.second)
        val names = ranked.map { it.hit.name to it.hit.osm }
        assertTrue(names.contains("Liberty Market" to "n1"))
        assertFalse("Urdu name of the same feature is grouped", names.any { it.second == "n1" && it.first != "Liberty Market" })
        assertEquals("street ways next to the market are grouped into it", 1, names.count { it.first == "Liberty Market" })
        assertEquals("two branches 9 km apart stay separate", 2, names.count { it.first == "Liberty Books" })
        // Same name and kind within 300 m without OSM ids (schema 1 database): grouped.
        val oldA = OfflineSearch.Hit("Hafeez Centre", "mall", 31.5160, 74.3430)
        val oldB = OfflineSearch.Hit("Hafeez Centre", "mall", 31.5165, 74.3433)
        assertEquals(1, OfflineSearch.rank(listOf(oldA, oldB), "hafeez", null, null).size)
        // A generic row ("place") and a specific one with the same name nearby: one result, the specific one shown.
        val generic = OfflineSearch.Hit("Liberty Heights", "place", 31.5100, 74.3440, osm = "n7")
        val store = OfflineSearch.Hit("Liberty Heights", "department_store", 31.5120, 74.3450, osm = "n8")
        assertEquals(listOf("department_store"), OfflineSearch.rank(listOf(generic, store), "liberty", liberty.first, liberty.second).map { it.hit.kind })
    }

    @Test
    fun resultLabelsHaveNoCoordinatesOrClutter() {
        assertEquals("Restaurant · MM Alam Road · Gulberg III",
            OfflineSearch.detail(OfflineSearch.Hit("Cosa Nostra", "restaurant", 31.5, 74.3, "Gulberg III", "MM Alam Road")))
        assertEquals("Suburb · Upper Mall", OfflineSearch.detail(OfflineSearch.Hit("G.O.R.", "suburb", 31.5, 74.3, "Upper Mall")))
        // Generic kinds are dropped, and the area is not repeated when it is the name.
        assertEquals("Gulberg", OfflineSearch.detail(OfflineSearch.Hit("12 Main Boulevard", "place", 31.5, 74.3, "Gulberg", "Main Boulevard")))
        assertEquals("", OfflineSearch.detail(OfflineSearch.Hit("Gulberg", "place", 31.5, 74.3, "Gulberg")))
        assertEquals("Place of worship", OfflineSearch.kindLabel("place_of_worship"))
        assertEquals("450 m", OfflineSearch.distanceLabel(470.0))
        assertEquals("4.2 km", OfflineSearch.distanceLabel(4200.0))
        assertEquals("35 km", OfflineSearch.distanceLabel(35400.0))
        assertEquals("", OfflineSearch.distanceLabel(Double.NaN))
    }

    @Test
    fun savedPlacesStarRecentsHomeWork() {
        val prefs = TestPrefs()
        val saved = SavedPlaces(prefs)
        val market = OfflineDestinationPicker.Place("Liberty Market", "marketplace", 31.5102, 74.3441, "Marketplace · Gulberg")
        val gor = OfflineDestinationPicker.Place("G.O.R.", "suburb", 31.56, 74.33)
        assertFalse(saved.isStarred(market))
        assertTrue(saved.toggleStar(market))
        assertTrue(SavedPlaces(prefs).isStarred(market.copy(lat = market.lat + 0.0001)))
        assertFalse(saved.toggleStar(market))
        assertTrue(saved.starred().isEmpty())
        saved.addRecent(market); saved.addRecent(gor); saved.addRecent(market)
        assertEquals(listOf("Liberty Market", "G.O.R."), SavedPlaces(prefs).recents().map { it.name })
        assertEquals("Marketplace · Gulberg", saved.recents().first().detail)
        repeat(12) { saved.addRecent(OfflineDestinationPicker.Place("P$it", "place", 31.5, 74.3 + it * 0.01)) }
        assertEquals(SavedPlaces.MAX_RECENT, saved.recents().size)
        saved.clearRecents()
        assertTrue(saved.recents().isEmpty())
        saved.setHome(gor); saved.setWork(market)
        assertEquals("G.O.R.", SavedPlaces(prefs).home()?.name)
        assertEquals("Liberty Market", SavedPlaces(prefs).work()?.name)
        saved.setHome(null)
        assertEquals(null, saved.home())
        // Damaged values are ignored, never thrown.
        val broken = TestPrefs(Keys.OFFLINE_PLACES_RECENT to "not json", Keys.OFFLINE_PLACE_HOME to "{\"name\":\"x\"}")
        assertTrue(SavedPlaces(broken).recents().isEmpty())
        assertEquals(null, SavedPlaces(broken).home())
    }
}
