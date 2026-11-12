package app.flare.auto

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen offline map, edge to edge (no header): a round back button top-left (the system back button works
 * too), the next-turn card beside it and the trip card bottom-left while navigating ([OfflineGuidance]), and the
 * map's own controls on the right. Without the navigation pack a one-line notice says why navigation is unavailable.
 */
class OfflineMapActivity : Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var card: OfflineMapCard
    private lateinit var stage: FrameLayout
    private lateinit var notice: TextView
    private var guidance: OfflineGuidance? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val ui = UiKit(this)
        window.immersive()
        window.setBackgroundDrawable(ColorDrawable(ui.t.background))
        stage = FrameLayout(this).apply { setBackgroundColor(ui.t.background) }
        card = OfflineMapCard(this, this)
        stage.addView(card, FrameLayout.LayoutParams(MATCH, MATCH))
        val edge = dp(16)
        stage.addView(mapButton(MapIcon("back", NavColors.TEXT), "Back") { finish() },
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply { setMargins(edge, edge, 0, 0) })
        notice = TextView(this).apply {
            textSize = 12f
            setTextColor(NavColors.TEXT)
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = NavColors.card(this@OfflineMapActivity)
        }
        stage.addView(notice, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(edge + dp(60), edge, 0, 0) })
        setContentView(stage)
        OfflineRouting.worker.execute {
            val status = NavigationPack.verified(this)
            runOnUiThread { if (!isDestroyed) attach(status) }
        }
    }

    private fun attach(status: NavigationPack.Status) {
        if (status !is NavigationPack.Status.Ready) {
            notice.text = status.message
            notice.visibility = View.VISIBLE
            return
        }
        val panel = OfflineGuidance(this, this, status.pack, card)
        guidance = panel
        val edge = dp(16)
        // Turn card: right of the back button, never under the right-hand controls.
        val width = minOf(dp(420), resources.displayMetrics.widthPixels - dp(16 + 48 + 12) - dp(16 + 48 + 16) - dp(12))
        stage.addView(panel.turnCard, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(edge + dp(60), edge, 0, 0) })
        panel.turnCard.minimumWidth = dp(220)
        panel.size(width)
        stage.addView(panel.tripCard, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(edge, 0, 0, edge) })
        card.onLocation = panel::update
        card.onSearch = panel::search
        card.onVoiceChanged = panel::voiceChanged
        card.onLongPress = { lat, lon ->
            ThemedDialogs.Builder(this).setTitle("Drive here?").setMessage("Start a route to the point you held on the map.")
                .setPositiveButton("Drive") { _, _ -> panel.choose(OfflineDestinationPicker.Place("Selected point", "coordinate", lat, lon)) }
                .setNegativeButton("Cancel", null).show()
        }
        // Older launchers opened the map with search: keep honouring the extra.
        if (intent.getBooleanExtra(EXTRA_SEARCH, false) && !panel.active) panel.search()
    }

    override fun onResume() {
        super.onResume()
        card.start()
    }

    override fun onPause() {
        card.stop()
        super.onPause()
    }

    override fun onDestroy() {
        card.onLocation = null
        card.onLongPress = null
        card.onSearch = null
        card.onVoiceChanged = null
        guidance?.close()
        super.onDestroy()
    }

    companion object {
        /** Boolean extra: open destination search as soon as the navigation pack is ready. */
        const val EXTRA_SEARCH = "app.flare.auto.extra.SEARCH"
    }
}
