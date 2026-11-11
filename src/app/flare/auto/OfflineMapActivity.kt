package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout

class OfflineMapActivity:Activity() {
    private lateinit var card:OfflineMapCard
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);val ui=UiKit(this)
        window.immersive();window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        val root=ui.column().apply { setBackgroundColor(ui.t.background) };root.addView(ui.header("Offline maps"))
        card=OfflineMapCard(this,this);root.addView(card,LinearLayout.LayoutParams(MATCH,0,1f));setContentView(root)
    }
    override fun onResume() { super.onResume();card.start() }
    override fun onPause() { card.stop();super.onPause() }
}
