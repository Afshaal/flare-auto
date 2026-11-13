package app.flare.auto

import android.database.sqlite.SQLiteDatabase
import java.text.Normalizer
import java.util.Locale

/**
 * Offline place search over the navigation pack's `search.sqlite`, fully offline.
 *
 * Works with both database layouts:
 * * schema 1 (pack revision ≤ 3): `places(id,name,kind,lat,lon)` + FTS4 `places_fts(name)`;
 * * schema 2 (revision 4+): adds `area`, `street`, `norm` (punctuation-free name, see [normalize]) and `osm`
 *   (source feature) columns and FTS4 `places_norm(norm)`, so "gor" finds "G.O.R." and results get a place hint.
 *
 * Results are ranked by text match first, then by place type ([tier]: cities, towns, suburbs and areas before
 * shops and streets, generic rows last), then by distance from the reference point (GPS fix or map centre), with
 * true duplicates grouped (see [group]). The pure parts are unit tested (OfflineSearchTest).
 */
internal object OfflineSearch {
    /** One database row. [area] / [street] / [osm] are empty with a schema 1 database. */
    data class Hit(val name: String, val kind: String, val lat: Double, val lon: Double,
                   val area: String = "", val street: String = "", val osm: String = "") {
        val norm: String by lazy { normalize(name) }
    }

    /** A hit with its rank and distance from the reference point (metres, NaN when there is none). */
    data class Ranked(val hit: Hit, val score: Int, val metres: Double)

    private val JOIN = Regex("[.'`‘’ʼ]")
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{M}+")

    /**
     * Lower case, accents dropped, dots and apostrophes removed (joining the letters around them), any other
     * punctuation becomes a space: "G.O.R. I" -> "gor i", "McDonald's" -> "mcdonalds", "Model-Town" -> "model town".
     * Identical to normalize() in flare-lahore-tools/tools/build_search.py.
     */
    fun normalize(text: String): String {
        val folded = MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD), "")
        return SPLIT.split(JOIN.replace(folded, "")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Text match quality of [name] (normalized) for [query] (normalized), lower is better:
     * 0 exact, 1 the name starts with the query, 2 every query word starts a name word in order,
     * 3 every query word starts some name word, 4 the name contains the query without spaces ("gulberg3"),
     * 5 anything else the full-text index returned.
     */
    fun score(name: String, query: String): Int {
        if (query.isEmpty()) return 5
        if (name == query) return 0
        if (name.startsWith(query)) return 1
        val words = name.split(' ')
        val terms = query.split(' ')
        var at = 0
        var ordered = true
        for (term in terms) {
            val found = (at until words.size).firstOrNull { words[it].startsWith(term) }
            if (found == null) { ordered = false; break }
            at = found + 1
        }
        if (ordered) return 2
        if (terms.all { term -> words.any { it.startsWith(term) } }) return 3
        if (name.replace(" ", "").contains(query.replace(" ", ""))) return 4
        return 5
    }

    /**
     * Ranks [hits] for [query] (raw text) and groups true duplicates: the English, Urdu and address names of one
     * OSM feature, and rows with the same normalized name and kind close together (streets are split into many
     * OSM ways, so they group within [STREET_GROUP_M]). Distinct places with the same name stay separate.
     */
    fun rank(hits: List<Hit>, query: String, refLat: Double?, refLon: Double?, limit: Int = 40): List<Ranked> {
        val q = normalize(query)
        val ranked = hits.map { hit ->
            val metres = if (refLat != null && refLon != null) NavGeometry.distance(refLat, refLon, hit.lat, hit.lon) else Double.NaN
            Ranked(hit, score(hit.norm, q), metres)
        }.sortedWith(compareBy<Ranked>({ it.score }, { tier(it.hit.kind) }, { if (it.metres.isNaN()) 0.0 else it.metres }, { it.hit.name.length }))
        return group(ranked).take(limit)
    }

    /**
     * Place type order among equally good text matches, lower first: city, town, suburb-like areas, villages,
     * other named places (shops, landmarks…), streets, then generic rows ("place", "yes": addresses, untyped areas).
     */
    fun tier(kind: String): Int = when (kind) {
        "city" -> 0
        "town" -> 1
        "suburb", "borough", "quarter", "neighbourhood", "area", "residential" -> 2
        "village", "hamlet", "locality", "isolated_dwelling" -> 3
        "street" -> 5
        in GENERIC -> 6
        else -> 4
    }

    /**
     * Keeps one row per group of duplicates, at the position of the best ranked one; input order is preserved.
     * Duplicates: the same OSM feature, or the same normalized name close together with the same kind, or where one
     * of them is generic ("place", "street": an address node, an untyped area, the road named after a market). When
     * a generic row and a specific one ("Marketplace") are grouped, the specific row is shown.
     */
    fun group(ranked: List<Ranked>): List<Ranked> {
        val kept = ArrayList<Ranked>()
        val features = HashSet<String>()
        for (r in ranked) {
            val hit = r.hit
            if (hit.osm.isNotEmpty() && !features.add(hit.osm)) continue
            val index = kept.indexOfFirst { k ->
                val other = k.hit
                other.norm == hit.norm && (other.kind == hit.kind || other.kind in GENERIC || hit.kind in GENERIC) &&
                    NavGeometry.distance(other.lat, other.lon, hit.lat, hit.lon) <
                    (if (other.kind == "street" && hit.kind == "street") STREET_GROUP_M else PLACE_GROUP_M)
            }
            if (index < 0) kept.add(r)
            else if (kept[index].hit.kind in GENERIC && hit.kind !in GENERIC) kept[index] = r.copy(score = kept[index].score, metres = kept[index].metres)
        }
        return kept
    }

    /** Kinds that say nothing about the place itself (see build_search.py). */
    private val GENERIC = setOf("place", "street", "yes")

    /**
     * Second line of a result: kind, then street and area when the database has them ("Restaurant · MM Alam Road ·
     * Gulberg III"). The area is left out when it is the name itself.
     */
    fun detail(hit: Hit): String {
        val parts = ArrayList<String>()
        val kind = kindLabel(hit.kind)
        if (hit.kind != "place" && hit.kind != "coordinate" && hit.kind != "saved") parts.add(kind)
        if (hit.street.isNotEmpty() && normalize(hit.street) != hit.norm && !hit.norm.endsWith(normalize(hit.street))) parts.add(hit.street)
        if (hit.area.isNotEmpty() && normalize(hit.area) != hit.norm) parts.add(hit.area)
        return parts.joinToString(" · ")
    }

    /** OSM tag value as a label: "fast_food" -> "Fast food", "place_of_worship" -> "Place of worship". */
    fun kindLabel(kind: String) = kind.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** "850 m" / "4.2 km" / "35 km" for a result's distance hint. */
    fun distanceLabel(metres: Double): String = when {
        metres.isNaN() -> ""
        metres < 1000 -> "${((metres / 50).toInt() * 50).coerceAtLeast(50)} m"
        metres < 10000 -> String.format(Locale.US, "%.1f km", metres / 1000)
        else -> "${(metres / 1000).toInt()} km"
    }

    /** FTS4 MATCH expression: every word as a prefix term ("liberty mar" -> "liberty"* "mar"*), at most six words. */
    fun matchTerms(words: List<String>): String =
        words.filter { term -> term.any(Char::isLetterOrDigit) }.take(6).joinToString(" ") { "\"" + it.replace("\"", "\"\"") + "\"*" }

    private val schemaCache = HashMap<String, Boolean>()

    /** Blocking (routing/search thread). Up to [CANDIDATES] nearest matches plus the best text matches anywhere. */
    fun find(db: java.io.File, query: String, refLat: Double?, refLon: Double?): List<Ranked> {
        val database = SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { d ->
            val key = db.absolutePath + ":" + db.lastModified() + ":" + db.length()
            val normalized = synchronized(schemaCache) {
                schemaCache.getOrPut(key) {
                    d.rawQuery("SELECT count(*) FROM sqlite_master WHERE name='places_norm'", null).use { it.moveToFirst() && it.getInt(0) > 0 }
                }
            }
            val terms = if (normalized) matchTerms(normalize(query).split(' ')) else matchTerms(query.split(Regex("\\s+")))
            if (terms.isBlank()) return@use emptyList()
            val columns = if (normalized) "p.id,p.name,p.kind,p.lat,p.lon,p.area,p.street,p.osm" else "p.id,p.name,p.kind,p.lat,p.lon"
            val join = if (normalized) "places_norm f ON p.id=f.rowid WHERE places_norm" else "places_fts f ON p.id=f.rowid WHERE places_fts"
            val rows = LinkedHashMap<Long, Hit>()
            fun read(sql: String, args: Array<String>) = d.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (rows.containsKey(id)) continue
                    rows[id] = Hit(c.getString(1) ?: "", c.getString(2) ?: "place", c.getDouble(3), c.getDouble(4),
                        if (normalized) c.getString(5) ?: "" else "", if (normalized) c.getString(6) ?: "" else "",
                        if (normalized) c.getString(7) ?: "" else "")
                }
            }
            if (refLat != null && refLon != null) {
                val kx = Math.cos(Math.toRadians(refLat))
                read("SELECT $columns FROM places p JOIN $join MATCH ? ORDER BY " +
                    "(p.lat-?)*(p.lat-?)+(p.lon-?)*(p.lon-?)*${kx * kx} LIMIT $CANDIDATES",
                    arrayOf(terms, refLat.toString(), refLat.toString(), refLon.toString(), refLon.toString()))
            }
            // Short names that match the query best anywhere (e.g. a suburb far from the car), by name length.
            read("SELECT $columns FROM places p JOIN $join MATCH ? ORDER BY length(p.name) LIMIT $CANDIDATES", arrayOf(terms))
            rank(rows.values.toList(), query, refLat, refLon)
        }
    }

    private const val CANDIDATES = 200
    private const val PLACE_GROUP_M = 500.0
    private const val STREET_GROUP_M = 1500.0
}
