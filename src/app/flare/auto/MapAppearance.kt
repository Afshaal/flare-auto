package app.flare.auto

import android.content.Context
import org.json.JSONObject

/**
 * MapLibre map appearance ("offline_map_style"): follow the launcher's light/dark theme (default), always dark, or
 * always light. The light look is made from the pack's own dark style at load time (same map data and layers, other
 * colours), so no pack changes are needed; the Mapsforge renderer keeps its own appearance.
 */
internal object MapAppearance {
    val CHOICES = arrayOf("Follow launcher", "Dark", "Light")
    private val VALUES = arrayOf("follow", "dark", "light")

    fun index(context: Context): Int =
        VALUES.indexOf(ProfileStore.preferences(context).getString(Keys.OFFLINE_MAP_STYLE, "follow")).coerceAtLeast(0)

    fun set(context: Context, index: Int) {
        ProfileStore.preferences(context).edit().putString(Keys.OFFLINE_MAP_STYLE, VALUES[index.coerceIn(0, 2)]).apply()
    }

    /** Whether the MapLibre map is drawn light now. */
    fun light(context: Context): Boolean = when (VALUES[index(context)]) {
        "light" -> true
        "dark" -> false
        else -> ThemeTokens.activeIndex(context) >= 5 // the light themes
    }

    /** 3D building colour on the light map (the dark one stays as built into [VectorMapView]). */
    const val LIGHT_BUILDINGS = "#d9d5ce"

    /**
     * Light colours for the pack style's layers: soft warm off-white land, white minor roads with subtle grey
     * casings, warmer major roads, muted sage parks, blue-grey water, light-grey buildings and charcoal labels.
     * Layers not listed keep their colours; geometry, zoom rules and fonts are untouched.
     */
    private val LIGHT: Map<String, Map<String, String>> = mapOf(
        "background" to mapOf("background-color" to "#ece9e3"),
        "green" to mapOf("fill-color" to "#d3dcc6"),
        "water" to mapOf("fill-color" to "#b9cad6"),
        "waterway" to mapOf("line-color" to "#a8bccb"),
        "building" to mapOf("fill-color" to "#dedad3", "fill-outline-color" to "#cbc6be"),
        "rail" to mapOf("line-color" to "#b5b0a8"),
        "road-casing" to mapOf("line-color" to "#c9c4bb"),
        "road-service" to mapOf("line-color" to "#ffffff"),
        "road-minor" to mapOf("line-color" to "#ffffff"),
        "road-secondary" to mapOf("line-color" to "#fdf9f1"),
        "road-primary" to mapOf("line-color" to "#faefdb"),
        "road-major" to mapOf("line-color" to "#f4e2bf"),
        "road-label" to mapOf("text-color" to "#3b3e43", "text-halo-color" to "#f7f5f1"),
        "place-label" to mapOf("text-color" to "#2c2f33", "text-halo-color" to "#f7f5f1"))

    /** The pack's style JSON recoloured for the light map; on any problem the original is returned. */
    fun lightStyle(json: String): String = try {
        val style = JSONObject(json)
        val layers = style.getJSONArray("layers")
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val colors = LIGHT[layer.optString("id")] ?: continue
            val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
            colors.forEach { (key, value) -> paint.put(key, value) }
        }
        style.put("name", style.optString("name", "Flare") + " (light)")
        style.toString()
    } catch (_: Exception) { json }
}
