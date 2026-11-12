package app.flare.auto

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Starred places, the Home and Work slots and recent destinations for offline search. Stored as small JSON
 * values in the driver's own preferences (ProfileStore.preferences), so every profile keeps its own list.
 */
internal class SavedPlaces(private val p: SharedPreferences) {
    fun home(): OfflineDestinationPicker.Place? = one(Keys.OFFLINE_PLACE_HOME)
    fun work(): OfflineDestinationPicker.Place? = one(Keys.OFFLINE_PLACE_WORK)
    fun setHome(place: OfflineDestinationPicker.Place?) = setOne(Keys.OFFLINE_PLACE_HOME, place)
    fun setWork(place: OfflineDestinationPicker.Place?) = setOne(Keys.OFFLINE_PLACE_WORK, place)

    fun starred(): List<OfflineDestinationPicker.Place> = list(Keys.OFFLINE_PLACES_STARRED)
    fun recents(): List<OfflineDestinationPicker.Place> = list(Keys.OFFLINE_PLACES_RECENT)

    fun isStarred(place: OfflineDestinationPicker.Place) = starred().any { same(it, place) }

    /** Stars or unstars [place]; returns the new state. */
    fun toggleStar(place: OfflineDestinationPicker.Place): Boolean {
        val current = starred()
        val starred = current.none { same(it, place) }
        save(Keys.OFFLINE_PLACES_STARRED, if (starred) listOf(place) + current else current.filterNot { same(it, place) }, MAX_STARRED)
        return starred
    }

    /** Most recent first, without duplicates, at most [MAX_RECENT]. Coordinates-only picks are kept too. */
    fun addRecent(place: OfflineDestinationPicker.Place) =
        save(Keys.OFFLINE_PLACES_RECENT, listOf(place) + recents().filterNot { same(it, place) }, MAX_RECENT)

    fun clearRecents() { p.edit().remove(Keys.OFFLINE_PLACES_RECENT).apply() }

    private fun one(key: String) = p.getString(key, null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }
    private fun setOne(key: String, place: OfflineDestinationPicker.Place?) {
        if (place == null) p.edit().remove(key).apply() else p.edit().putString(key, encode(place).toString()).apply()
    }

    private fun list(key: String): List<OfflineDestinationPicker.Place> {
        val raw = p.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { runCatching { decode(array.getJSONObject(it)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    private fun save(key: String, places: List<OfflineDestinationPicker.Place>, max: Int) {
        val array = JSONArray()
        places.take(max).forEach { array.put(encode(it)) }
        p.edit().putString(key, array.toString()).apply()
    }

    companion object {
        const val MAX_RECENT = 8
        const val MAX_STARRED = 30

        /** Same place: same name and within ~30 m. */
        fun same(a: OfflineDestinationPicker.Place, b: OfflineDestinationPicker.Place) =
            a.name == b.name && NavGeometry.distance(a.lat, a.lon, b.lat, b.lon) < 30

        fun encode(place: OfflineDestinationPicker.Place): JSONObject = JSONObject()
            .put("name", place.name).put("kind", place.kind).put("lat", place.lat).put("lon", place.lon).put("detail", place.detail)

        fun decode(o: JSONObject): OfflineDestinationPicker.Place {
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
            return OfflineDestinationPicker.Place(o.getString("name"), o.optString("kind", "place"), lat, lon, o.optString("detail", ""))
        }
    }
}
