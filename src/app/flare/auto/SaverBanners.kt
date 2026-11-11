package app.flare.auto
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

internal object BannerNotifications {
    data class Entry(val key:String,val pkg:String,val title:String,val text:String,val action:PendingIntent?,val music:Boolean=false)
    interface Listener { fun posted(entry:Entry); fun removed(key:String) }
    private var target=WeakReference<Listener>(null)
    fun listen(listener:Listener) { target=WeakReference(listener) }
    fun stop(listener:Listener) { if(target.get()===listener) target.clear() }
    fun posted(sbn:StatusBarNotification) {
        val receiver=target.get() ?: return
        val n=sbn.notification
        if(n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)!=0 || n.visibility==Notification.VISIBILITY_SECRET) return
        try { receiver.posted(Entry(sbn.key,sbn.packageName,n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(160),
            n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(320),n.contentIntent)) } catch (_: RuntimeException) { }
    }
    fun removed(key:String) { target.get()?.removed(key) }
}

internal class SaverBanners(private val activity:Activity,private val root:FrameLayout,private val occupied:(Boolean,Boolean)->Unit={_,_->}):BannerNotifications.Listener {
    private val prefs=ProfileStore.preferences(activity)
    private val theme=ThemeTokens.get(activity)
    private val handler=Handler(Looper.getMainLooper())
    private val queue=BannerQueue<BannerNotifications.Entry> { it.key }
    private val cards=LinkedHashMap<View,Boolean>()
    private fun updateSlots() { occupied(true,cards.values.any { it });occupied(false,cards.values.any { !it }) }
    fun song(pkg:String,title:String,artist:String) { shown?.takeUnless { it.music }?.let { queue.offer(it) };show(BannerNotifications.Entry("music",pkg,title,artist,null,true)) }
    private var nextLeft=true
    private var shown:BannerNotifications.Entry?=null
    private var banner:View?=null
    private val expire=Runnable { dismiss() }
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    fun start() { if(prefs.getBoolean("saver_banners",false)) BannerNotifications.listen(this) }
    fun stop() {
        BannerNotifications.stop(this); handler.removeCallbacksAndMessages(null); queue.clear()
        cards.keys.toList().forEach { it.animate().cancel();root.removeView(it) };cards.clear();updateSlots();banner=null;shown=null
    }
    override fun posted(entry:BannerNotifications.Entry) {
        if(entry.pkg==activity.packageName || !prefs.getStringSet("saver_banner_apps",emptySet())!!.contains(entry.pkg)) return
        if(shown?.key==entry.key) show(entry) else if(shown==null) show(entry) else queue.offer(entry)
    }
    override fun removed(key:String) { queue.remove(key); if(shown?.key==key) dismiss() }
    private fun dismiss() {
        handler.removeCallbacks(expire)
        val old=banner;banner=null;shown=null
        fun done() { old?.let { root.removeView(it);cards.remove(it) };updateSlots();queue.next()?.let { show(it) } }
        if(old!=null && Motion.enabled(activity)) {
            val left=cards[old]==true
            old.animate().cancel();old.animate().translationX((if(left) -1 else 1)*(old.width+dp(40)).toFloat()).alpha(0f).setDuration(280)
                .setInterpolator(android.view.animation.PathInterpolator(.2f,.8f,.2f,1f)).withEndAction { done() }.start()
        } else done()
    }
    private fun show(entry:BannerNotifications.Entry) {
        handler.removeCallbacks(expire); banner?.animate()?.cancel(); banner?.let { root.removeView(it);cards.remove(it) }
        shown=entry
        val scale=prefs.getInt("saver_banner_size",100).coerceIn(85,120)/100f
        val card=LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12))
            background=GlassDrawable(theme.card,dp(16).toFloat(),theme.cardBorder)
            elevation=dp(6).toFloat()
        }
        val icon=ImageView(activity).apply { setImageDrawable(AsyncImages.load(activity) { c -> try { c.packageManager.getApplicationIcon(entry.pkg) } catch(_:Exception) { null } }) }
        card.addView(icon,LinearLayout.LayoutParams(dp(32),dp(32)).apply { rightMargin=dp(12) })
        val labels=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        val appName=try { activity.packageManager.getApplicationInfo(entry.pkg,0).loadLabel(activity.packageManager).toString() } catch(_:Exception) { entry.pkg }
        fun label(value:String,size:Float,color:Int)=TextView(activity).apply {
            text=value; textSize=size*scale; setTextColor(color); maxLines=2; ellipsize=TextUtils.TruncateAt.END; includeFontPadding=false
        }
        labels.addView(label(appName,12f,theme.textSecondary))
        val private=!entry.music && prefs.getBoolean("saver_banner_private",true)
        labels.addView(label(if(private) "New notification" else entry.title,16f,theme.textPrimary))
        if(!private && entry.text.isNotEmpty()) labels.addView(label(entry.text,14f,theme.textSecondary))
        card.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        val width=minOf(dp((340*scale).toInt()),activity.resources.displayMetrics.widthPixels-dp(32))
        val side=if(entry.music) "right" else prefs.getString("saver_banner_position","left")
        val left=side=="left" || (side=="alternate" && nextLeft)
        if(side=="alternate") nextLeft=!nextLeft
        root.addView(card,FrameLayout.LayoutParams(width,-2,Gravity.TOP or if(left) Gravity.START else Gravity.END).apply { topMargin=dp(18); leftMargin=dp(18); rightMargin=dp(18) })
        cards[card]=left;updateSlots()
        card.setOnClickListener {
            try { entry.action?.send(); if(entry.action!=null) activity.finish() else dismiss() }
            catch(_:PendingIntent.CanceledException) { dismiss() }
        }
        var startX=0f
        card.setOnTouchListener { v,e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> startX=e.x
                MotionEvent.ACTION_UP -> if(kotlin.math.abs(e.x-startX)>dp(40)) { dismiss(); return@setOnTouchListener true }
            }; false
        }
        banner=card
        if(Motion.enabled(activity)) {
            card.alpha=0f;card.translationX=(if(left) -1 else 1)*(width+dp(40)).toFloat()
            card.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(android.view.animation.PathInterpolator(.2f,.8f,.2f,1f)).start()
        }
        handler.postDelayed(expire,if(entry.music) 5000 else prefs.getInt("saver_banner_seconds",5).coerceIn(2,15)*1000L)
    }
}
