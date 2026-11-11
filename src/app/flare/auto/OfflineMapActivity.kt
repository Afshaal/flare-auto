package app.flare.auto

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen offline map. When the navigation pack is installed, an [OfflineGuidance] panel sits over the map
 * (destination search, route, turn-by-turn); otherwise a one-line notice says why navigation is unavailable.
 */
class OfflineMapActivity : Activity() {
    private lateinit var card: OfflineMapCard
    private lateinit var stage: FrameLayout
    private lateinit var notice: TextView
    private var guidance: OfflineGuidance? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val ui = UiKit(this)
        window.immersive()
        window.setBackgroundDrawable(ColorDrawable(ui.t.background))
        val root = ui.column().apply { setBackgroundColor(ui.t.background) }
        root.addView(ui.header("Offline maps"))
        stage = FrameLayout(this)
        card = OfflineMapCard(this, this)
        stage.addView(card, FrameLayout.LayoutParams(MATCH, MATCH))
        notice = ui.text("", 12, ui.t.textSecondary).apply {
            visibility = View.GONE
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GlassDrawable(ui.t.card, dp(8).toFloat(), ui.t.cardBorder)
        }
        stage.addView(notice, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), 0, 0) })
        root.addView(stage, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
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
        val panel = OfflineGuidance(this, this, status.pack, card::showRoute, card::center)
        guidance = panel
        val width = minOf(dp(380), resources.displayMetrics.widthPixels - dp(120))
        stage.addView(panel.view, FrameLayout.LayoutParams(width, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), 0, 0) })
        panel.size(width)
        card.onLocation = panel::update
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
        guidance?.close()
        super.onDestroy()
    }
}
