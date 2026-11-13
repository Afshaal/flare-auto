package app.flare.auto

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The light MapLibre look recolours the pack's own style and leaves everything else as it was. */
class MapAppearanceTest {
    private val dark = """{"version":8,"name":"Flare Night Drive","sources":{},"layers":[
        {"id":"background","type":"background","paint":{"background-color":"#0f1a2a"}},
        {"id":"road-minor","type":"line","paint":{"line-color":"#2a3a52","line-width":["interpolate",["linear"],["zoom"],13,0.6,17,10]}},
        {"id":"place-label","type":"symbol","layout":{"text-field":"{name}"},"paint":{"text-color":"#b5c3d6","text-halo-color":"#0f1a2a"}},
        {"id":"custom","type":"line","paint":{"line-color":"#123456"}}]}"""

    @Test
    fun lightStyleRecoloursKnownLayersOnly() {
        val light = JSONObject(MapAppearance.lightStyle(dark))
        val layers = light.getJSONArray("layers")
        fun paint(i: Int) = layers.getJSONObject(i).getJSONObject("paint")
        assertEquals("#ece9e3", paint(0).getString("background-color"))
        assertEquals("#ffffff", paint(1).getString("line-color"))
        // Widths, zoom rules and label fields are untouched.
        assertEquals(JSONObject(dark).getJSONArray("layers").getJSONObject(1).getJSONObject("paint").get("line-width").toString(),
            paint(1).get("line-width").toString())
        assertEquals("{name}", layers.getJSONObject(2).getJSONObject("layout").getString("text-field"))
        assertEquals("#2c2f33", paint(2).getString("text-color"))
        // A layer the light palette does not know keeps its colour.
        assertEquals("#123456", paint(3).getString("line-color"))
    }

    @Test
    fun brokenStyleIsReturnedUnchanged() {
        assertEquals("not json", MapAppearance.lightStyle("not json"))
    }
}
