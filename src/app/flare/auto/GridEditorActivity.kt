package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray

class GridEditorActivity:Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var ui:UiKit
    private lateinit var draft:EditorDraft
    private lateinit var previewContext:EditorContext
    private lateinit var pages:MutableList<MutableList<GridTile>>
    private var page=0
    private var defaultPage=0
    private var selected=-1
    private var dockSelected=-1
    private var placing:GridTile?=null
    private var replacement=-1
    private lateinit var preview:PagePreview
    private lateinit var screen:LinearLayout
    private lateinit var trash:PageTrash
    private var previewY=0f
    private var deleteDragging=false
    private lateinit var panel:LinearLayout
    private lateinit var images:java.io.File
    private var imageToken=""
    private var pendingKey=""
    private var pendingDock=-1
    /** The app being picked links a CarPlay / Android Auto dock action instead of replacing it. */
    private var pendingLink=false
    /** The app being picked goes into a new dock slot at [pendingDock]. */
    private var pendingInsert=false
    private var activeBoard:GridBoard?=null
    private val mapCards=ArrayList<OfflineMapCard>()
    private var widgetReplacement=-1
    private val allocatedWidgets=mutableSetOf<Int>()
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        draft=EditorDraft(ProfileStore.preferences(this));state?.getBundle("settings")?.let { draft.restore(it) }
        imageToken=state?.getString("imageToken") ?: java.util.UUID.randomUUID().toString()
        images=java.io.File(cacheDir,"home_editor_"+imageToken).apply { mkdirs() }
        previewContext=EditorContext(UiScale.wrap(this,Keys.UI_SCALE_HOME),draft,images)
        pages=DashboardPages.decode(state?.getString("draft")) ?: DashboardPages.initial(draft)
        page=(state?.getInt("page") ?: draft.getInt(Keys.DASHBOARD_DEFAULT,0)).coerceIn(-1,pages.size)
        defaultPage=(state?.getInt("default") ?: draft.getInt(Keys.DASHBOARD_DEFAULT,0)).coerceIn(0,pages.lastIndex)
        pendingKey=state?.getString("pendingKey").orEmpty();pendingDock=state?.getInt("pendingDock",-1) ?: -1
        widgetReplacement=state?.getInt("widgetReplacement",-1) ?: -1
        state?.getIntArray("allocatedWidgets")?.let { allocatedWidgets.addAll(it.toList()) }
        render()
    }
    override fun onSaveInstanceState(out:Bundle) {
        out.putInt("widgetReplacement",widgetReplacement);out.putIntArray("allocatedWidgets",allocatedWidgets.toIntArray())
        out.putString("imageToken",imageToken)
        out.putBundle("settings",draft.bundle());out.putString("draft",DashboardPages.encode(pages));out.putInt("page",page);out.putInt("default",defaultPage)
        out.putString("pendingKey",pendingKey);out.putInt("pendingDock",pendingDock);super.onSaveInstanceState(out)
    }
    private fun edit(run:()->Unit) { run();render() }
    /** A context-bar action; [danger] (Remove) is drawn in red so it stands apart. */
    private fun command(row:LinearLayout,label:String,danger:Boolean=false,run:()->Unit) {
        val red=0xffe5484d.toInt()
        row.addView(ui.text(label,13,if(danger) red else ui.t.textPrimary).apply {
            gravity=Gravity.CENTER;minimumHeight=ui.dp(44)
            background=android.graphics.drawable.GradientDrawable().apply { setColor(ui.t.card);cornerRadius=ui.dp(14).toFloat();setStroke(ui.dp(1),if(danger) red else ui.t.cardBorder) }
            setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6));setOnClickListener { run() }
        },LinearLayout.LayoutParams(0,WRAP,1f).apply { setMargins(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4)) })
    }
    private fun clearSelection() {
        selected=-1;dockSelected=-1;activeBoard?.let { it.selection=-1;it.invalidate() };contextPanel()
    }
    /** Puts the widget being placed on the page (replacing the tile it was chosen for, if any). */
    private fun place(candidate:GridTile) { if(replacement>=0) pages[page][replacement]=candidate else pages[page].add(candidate);placing=null;selected=-1 }
    /**
     * A widget chosen in the gallery goes straight onto the page at [tile]'s position (replacing tile [replace], or
     * added) and is selected like a tapped widget: outline and four resize handles, normal appearance, movable.
     */
    private fun addChosen(tile:GridTile,replace:Int) {
        edit {
            val tiles=pages[page]
            if(replace in tiles.indices) { tiles[replace]=tile;selected=replace } else { tiles.add(tile);selected=tiles.lastIndex }
            placing=null;replacement=-1;dockSelected=-1
        }
    }
    private fun save() {
        // Saving while a new widget is still being placed keeps it where it is shown (it was dropped before).
        placing?.let { if(page in pages.indices) place(it) }
        draft.edit().putString(DashboardPages.KEY,DashboardPages.encode(pages)).putInt(Keys.DASHBOARD_DEFAULT,defaultPage).apply()
        val image=java.io.File(images,draft.getString(Keys.EDITOR_IMAGE,"missing") ?: "missing")
        if(image.isFile && draft.getInt(Keys.WALLPAPER_HOME,-1)==WallpaperPack.CUSTOM_INDEX) {
            try { java.nio.file.Files.move(image.toPath(),CustomImages.file(this,"home").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);CustomImages.cache.evictAll() }
            catch(_:Exception) { Toast.makeText(this,"Could not save the custom image",Toast.LENGTH_LONG).show();return }
        }
        draft.edit().remove(Keys.EDITOR_IMAGE).apply()
        val original=ProfileStore.preferences(this);val previous=HostedWidgets.ids(original.getString(DashboardPages.KEY,null))
        draft.save(original)
        (previous-HostedWidgets.ids(original.getString(DashboardPages.KEY,null))).forEach { HostedWidgets.delete(this,it) }
        ThemeTokens.invalidate();finish()
    }
    private fun control(kind:String,label:String,enabled:Boolean=true,filled:Boolean=false,run:()->Unit):View =
        EditorIcon(this,kind,ui.t.textPrimary,filled).apply {
            // Icon-only: the name shows on long press (tooltip) and is read by accessibility services.
            contentDescription=label;tooltipText=label;isEnabled=enabled;alpha=if(enabled) 1f else .25f
            background=android.graphics.drawable.GradientDrawable().apply {
                shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(ui.t.card)
                setStroke(ui.dp(1),if(filled) ui.t.textSecondary else ui.t.cardBorder)
            }
            setOnClickListener { run() }
        }
    private fun railButton(rail:LinearLayout,kind:String,label:String,enabled:Boolean=true,filled:Boolean=false,run:()->Unit) {
        rail.addView(control(kind,label,enabled,filled,run),LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply {
            gravity=Gravity.CENTER_HORIZONTAL;topMargin=ui.dp(6);bottomMargin=ui.dp(6)
        })
    }
    private fun render() {
        mapCards.forEach { it.stop() };mapCards.clear()
        if(::screen.isInitialized) screen.animate().cancel()
        deleteDragging=false
        ui=UiKit(this);ui.t=ThemeTokens.preview(draft)
        window.immersive();window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        val root=ui.column().apply { setBackgroundColor(ui.t.background) }
        activeBoard=null
        val stage=FrameLayout(this)
        root.addView(stage,LinearLayout.LayoutParams(MATCH,0,1f))
        preview=PagePreview(this,{ page in pages.indices && placing==null },::dragPage).apply { setOnClickListener { clearSelection() } }
        stage.addView(preview,FrameLayout.LayoutParams(MATCH,MATCH).apply { leftMargin=ui.dp(68);rightMargin=ui.dp(68) })
        screen=LinearLayout(previewContext).apply {
            orientation=LinearLayout.VERTICAL;background=WallpaperPack.home(previewContext,ui.t)
            outlineProvider=object:android.view.ViewOutlineProvider() {
                override fun getOutline(v:View,out:android.graphics.Outline) { out.setRoundRect(0,0,v.width,v.height,ui.dp(18).toFloat()) }
            }
            clipToOutline=true;elevation=ui.dp(12).toFloat();contentDescription="Home page preview including dock"
        }
        val width=resources.displayMetrics.widthPixels;val height=resources.displayMetrics.heightPixels
        preview.addView(screen,FrameLayout.LayoutParams(width,height))
        screen.pivotX=0f;screen.pivotY=0f
        preview.addOnLayoutChangeListener { v,_,_,_,_,_,_,_,_->
            val scale=minOf((v.width-ui.dp(8)).coerceAtLeast(1)/width.toFloat(),(v.height-ui.dp(16)).coerceAtLeast(1)/height.toFloat())
            screen.scaleX=scale;screen.scaleY=scale
            screen.translationX=(v.width-width*scale)/2;previewY=(v.height-height*scale)/2
            if(!deleteDragging) screen.translationY=previewY
        }
        trash=PageTrash(this).apply { alpha=0f;translationY=-ui.dp(40).toFloat() }
        preview.addView(trash,FrameLayout.LayoutParams(ui.dp(40),ui.dp(40),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        fun homeDp(n:Int)=(n*previewContext.resources.displayMetrics.density+.5f).toInt()
        // Side dock: the preview is a row of edge dock and the page column, as on Home; otherwise one padded column.
        val side=HomeChrome.dockSide(draft)
        val main=if(side=="bottom") screen else LinearLayout(previewContext).apply { orientation=LinearLayout.VERTICAL }
        val sideGap=homeDp(10)
        if(main!==screen) {
            // As on Home: no top bar, the same margin around the detached dock and the cards.
            screen.orientation=LinearLayout.HORIZONTAL;screen.setPadding(sideGap,sideGap,sideGap,sideGap)
            screen.addView(main,LinearLayout.LayoutParams(0,MATCH,1f))
        } else {
            val spacing=homeDp(draft.getInt(Keys.SPACING,14));main.setPadding(spacing,homeDp(10),spacing,homeDp(10))
            val top=HomeChrome.TopBar(previewContext,draft,ui.t)
            top.date.text=java.text.SimpleDateFormat("EEE, MMM d",java.util.Locale.getDefault()).format(java.util.Date())
            main.addView(top);main.addView(View(previewContext).apply { background=HomeChrome.accent(ui.t) },LinearLayout.LayoutParams(MATCH,homeDp(1)))
        }
        val controller=NowPlaying.active(this)
        val strip=CardPages(this,page,-1,pages.size,{ index,host->
            if(index !in pages.indices) {
                val add=ui.column().apply { gravity=Gravity.CENTER }
                add.addView(ui.text("+",100,ui.t.textPrimary).apply { alpha=.25f;gravity=Gravity.CENTER })
                add.addView(ui.text(if(pages.size<5) "Add page" else "Five pages maximum",16,ui.t.textSecondary))
                add.setOnClickListener { if(pages.size<5) edit { if(index<0) { pages.add(0,mutableListOf());defaultPage++;page=0 } else { pages.add(mutableListOf());page=pages.lastIndex } } }
                host.connectPlaceholder(add);add
            } else {
                val display=if(index==page && placing!=null) pages[index].filterIndexed { n,_->n!=replacement }.toMutableList().apply { add(placing!!) } else pages[index]
                val board=GridBoard(previewContext,display,true)
                board.selection=if(index==page && placing==null) selected else -1
                val content=HomeCards(this,draft,ui.t,preview=true,context=previewContext)
                display.forEach { tile->val cell=LinearLayout(previewContext);content.build(cell,tile.type,tile.h<5,style=tile.style);board.addView(CardFrame(previewContext,cell,tile.type)) }
                mapCards.addAll(content.maps)
                controller?.metadata?.let { metadata->
                    content.trackTitle?.text=NowPlaying.value(metadata,android.media.MediaMetadata.METADATA_KEY_TITLE)
                    content.trackArtist?.text=NowPlaying.value(metadata,android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    val art=metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                    art?.let { content.albumArt?.setImageDrawable(android.graphics.drawable.BitmapDrawable(resources,it)) }
                }
                if(index==page) activeBoard=board
                board.onChanged={ render() }
                board.onChoose={ selected=it;dockSelected=-1;contextPanel() }
                board.onCorner={ left->corner(left) }
                board.onPageDeleteDrag={ dy,end,cancel->dragPage(dy,end,cancel) }
                board.placement=placing
                board.onPlacement={ x,y->placing?.let { t->val candidate=t.copy(x=x.coerceIn(0,8-t.w),y=y.coerceIn(0,6-t.h));val others=pages[page].filterIndexed { n,_->n!=replacement };if(others.none { it.overlaps(candidate) }) { placing=candidate;render() } } }
                host.connect(board);board
            }
        },{ next->page=next;selected=-1;placing=null;render() })
        main.addView(strip,LinearLayout.LayoutParams(MATCH,0,1f).apply { if(side=="bottom") { topMargin=homeDp(12);bottomMargin=homeDp(12) } })
        // Page dots: bottom placement only; with a side dock they would push the cards above the dock's bottom edge.
        if(pages.size>1 && side=="bottom") main.addView(TextView(previewContext).apply {
            text=pages.indices.joinToString("  ") { if(it==page) "●" else "○" }
            textSize=13*draft.getInt(Keys.TEXT,100)/100f;setTextColor(ui.t.textSecondary);gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(MATCH,homeDp(28)))
        val dockClick={ index:Int,_:DockConfig.Item->
            selected=-1;activeBoard?.let { it.selection=-1;it.invalidate() };dockSelected=index;contextPanel();dockMenu(index)
        }
        val reorder={ from:Int,to:Int->
            val items=DockConfig.read(draft);val item=items.removeAt(from);items.add(to,item);DockConfig.save(draft,items)
        }
        if(side=="bottom") {
            val dock=HomeChrome.dock(previewContext,draft,ui.t,dockClick)
            EditorDockDrag(dock,{ screen.scaleX },reorder,{ render() })
            screen.addView(dock,LinearLayout.LayoutParams(MATCH,homeDp(draft.getInt(Keys.DOCK,70))))
        } else {
            val dock=HomeChrome.sideDock(previewContext,draft,ui.t,height-2*sideGap-2*homeDp(4),dockClick)
            EditorDockDrag(dock.shortcuts,{ screen.scaleX },reorder,{ render() })
            val params=LinearLayout.LayoutParams(homeDp(draft.getInt(Keys.DOCK,70)),MATCH)
            val inset=homeDp(4) // the page grid's own inset, so the dock lines up with the cards
            if(side=="left") screen.addView(dock.bar,0,params.apply { setMargins(inset,inset,sideGap,inset) }) else screen.addView(dock.bar,params.apply { setMargins(sideGap,inset,inset,inset) })
        }
        val real=page in pages.indices
        fun rail(side:Int):LinearLayout=ui.column().apply {
            gravity=Gravity.CENTER_HORIZONTAL;setPadding(0,ui.dp(4),0,ui.dp(4));setOnClickListener { clearSelection() }
            stage.addView(this,FrameLayout.LayoutParams(ui.dp(68),MATCH,side))
        }
        val left=rail(Gravity.START)
        railButton(left,"back","Back") { finish() }
        left.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        railButton(left,"layouts","Layouts",real) { presets() }
        railButton(left,"main",if(real && page==defaultPage) "Main page (this page)" else "Make this the main page",real,real && page==defaultPage) { if(defaultPage!=page) edit { defaultPage=page } }
        railButton(left,"reset","Reset page",real) { ThemedDialogs.Builder(this).setTitle("Reset this page?").setMessage("Restore the default clock and media cards on this page. Other pages and the dock stay as they are.")
            .setPositiveButton("Reset") { _,_->edit { pages[page]=defaultLayout();selected=-1;placing=null } }.setNegativeButton("Cancel",null).show() }
        left.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        railButton(left,"left","Move page left",real && page>0) { movePage(-1) }
        val right=rail(Gravity.END)
        right.addView(View(this),LinearLayout.LayoutParams(1,ui.dp(60)))
        right.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        railButton(right,"add","Add widget",real) { gallery(-1) }
        railButton(right,"appearance","Appearance") { appearance() }
        railButton(right,"save","Save") { save() }
        right.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        railButton(right,"right","Move page right",real && page<pages.lastIndex) { movePage(1) }
        panel=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(8),0,ui.dp(8),0) }
        root.addView(panel,LinearLayout.LayoutParams(MATCH,ui.dp(56)));setContentView(root);contextPanel()
    }
    private fun contextPanel() {
        panel.removeAllViews()
        val candidate=placing
        if(candidate!=null) {
            command(panel,"Place ${candidate.w}×${candidate.h}") { edit { place(candidate) } }
            command(panel,"Back to gallery") { gallery(replacement) };command(panel,"Cancel placement") { placing=null;render() };return
        }
        if(dockSelected>=0) {
            if(DockConfig.read(draft).getOrNull(dockSelected)?.action!=DockAction.APPS) command(panel,"Change shortcut") { chooseApp("",dockSelected) }
            command(panel,"Dock appearance") { dockAppearance() };return
        }
        val tile=pages.getOrNull(page)?.getOrNull(selected)
        if(tile==null) {
            // Nothing selected: which page this is, and whether Home opens on it.
            if(page in pages.indices) panel.addView(ui.text("Page ${page+1} of ${pages.size} · " +
                (if(page==defaultPage) "★ Main page: Home opens here" else "Tap ★ on the left to make this the main page") +
                " · Tap a widget to edit it",13,ui.t.textSecondary).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(MATCH,WRAP))
            return
        }
        command(panel,"Change widget") { gallery(selected) }
        if(tile.type in GridTile.CORNER_CLOCKS)
            command(panel,"Corner widgets") { ThemedDialogs.Builder(this).setTitle("Corner widgets").setItems(arrayOf("Top left: ${cornerName(true)}","Top right: ${cornerName(false)}")) { _,i->corner(i==0) }.show() }
        if(tile.type in GridTile.BRANDED_CLOCKS) command(panel,"Vehicle & badges") { appearance(1) }
        if(tile.type=="apps") command(panel,"Applications") { apps() }
        if(HostedWidgets.id(tile.type)!=null) command(panel,"Widget settings") {
            try { HostedWidgets.host(this).startAppWidgetConfigureActivityForResult(this,HostedWidgets.id(tile.type)!!,0,721,null) }
            catch(_:RuntimeException) { Toast.makeText(this,"This widget has no configuration screen",Toast.LENGTH_SHORT).show() }
        }
        if(tile.type=="projection" || tile.type=="clock") command(panel,"Projection apps") { ThemedDialogs.Builder(this).setTitle("Projection apps").setItems(arrayOf("CarPlay","Android Auto")) { _,i->chooseApp(if(i==0) Keys.APP_CARPLAY else Keys.APP_ANDROIDAUTO) }.show() }
        command(panel,"Remove",danger=true) { edit { pages[page].removeAt(selected);selected=-1 } }
    }
    private fun sample(type:String,w:Int,h:Int,style:String?=null):View {
        val cell=LinearLayout(this);val renderer=HomeCards(this,draft,ui.t,preview=true,sampleData=true,context=previewContext,duration={180000})
        renderer.build(cell,type,h<5,style=style);renderer.trackTitle?.text="Midnight Drive";renderer.trackArtist?.text="Sample artist"
        renderer.albumArt?.setImageDrawable(MediaGlyph("note",ui.t.accent));renderer.elapsed?.text="1:24";renderer.total?.text="3:00"
        return CardFrame(this,cell,type)
    }
    /**
     * "Add widget" / "Change widget": a full-screen drawer with the widget groups as tabs and the styles as cards
     * (live sample, name, the sizes that fit). Clocks have clock-style tabs: they restyle the previews only, and the
     * chosen style is stored on the widget added ([GridTile.style]). When adding, "This page / New page" in the title
     * bar picks where the widget goes (smart placement on this page; a new page holds up to five). Changing a widget
     * keeps its position, and its size first when the new style supports it. Choosing a size places the widget at
     * once and selects it ([addChosen]); installed app widgets have their own tab.
     */
    private fun gallery(index:Int) {
        if(page !in pages.indices) { Toast.makeText(this,"Tap + to create a page first",Toast.LENGTH_SHORT).show();return }
        val replacing=pages[page].getOrNull(index)
        val drawer=EditorDrawer(this,ui,if(replacing!=null) "Change widget" else "Add widget")
        val others=pages[page].filterIndexed { n,_->n!=index }
        var newPage=false
        var clockStyle=ClockStyles.ids.indexOf(replacing?.style ?: draft.getString(Keys.HOME_CLOCK_STYLE,"classic")).coerceAtLeast(0)
        fun styleFor(type:String)=if(type.startsWith("clock")) ClockStyles.ids[clockStyle] else null
        /** Where [type] at [w]x[h] would go on this page (keeping the replaced widget's position when it fits). */
        fun fitHere(type:String,w:Int,h:Int):GridTile? {
            val atOld=replacing?.copy(type=type,w=w,h=h)?.takeIf { it.valid() && others.none { t->t.overlaps(it) || t.type==it.type } }
            return atOld ?: WidgetCatalog.fit(others,type,w,h)
        }
        fun usable(type:String,w:Int,h:Int)=GridTile(type,0,0,w,h).valid() && (if(newPage) pages.size<5 else fitHere(type,w,h)!=null)
        fun choose(style:WidgetCatalog.Style,w:Int,h:Int) {
            val tileStyle=styleFor(style.type)
            if(newPage) {
                if(pages.size>=5) { Toast.makeText(this,"Five pages maximum",Toast.LENGTH_SHORT).show();return }
                pages.add(mutableListOf());page=pages.lastIndex;drawer.dismiss();addChosen(GridTile(style.type,0,0,w,h,tileStyle),-1);return
            }
            val fit=fitHere(style.type,w,h) ?: run { Toast.makeText(this,"No space on this page for ${w}×${h}",Toast.LENGTH_SHORT).show();return }
            drawer.dismiss();addChosen(fit.copy(style=tileStyle),index)
        }
        fun header() {
            if(replacing!=null) return
            drawer.headerAction(drawer.segmented(listOf("This page","New page"),if(newPage) 1 else 0) { i-> newPage=i==1;header();drawer.refresh() })
        }
        val groups=WidgetCatalog.styles.groupBy { it.group }
        val categories=groups.map { (group,styles)->
            val cards=styles.map { style-> {
                // Changing a widget: its current size first when this style supports it.
                val sizes=replacing?.let { r-> (r.w to r.h).takeIf { GridTile(style.type,0,0,it.first,it.second).valid() && it !in style.sizes } }
                    ?.let { listOf(it)+style.sizes } ?: style.sizes
                val chips=sizes.map { (w,h)->
                    val ok=usable(style.type,w,h)
                    val current=replacing!=null && replacing.w==w && replacing.h==h
                    EditorDrawer.Chip(if(current) "Keep ${w}×${h}" else "${w}×${h}",ok,primary=ok) { choose(style,w,h) }
                }
                val note=when {
                    chips.any { it.enabled } -> null
                    newPage -> "Five pages maximum"
                    replacing!=null -> "No space on this page for this widget"
                    else -> "No space on this page · choose New page above"
                }
                val preview=style.sizes.first()
                drawer.card(sample(style.type,preview.first,preview.second,styleFor(style.type)),style.name,chips,note)
            } }
            if(group=="Clocks") EditorDrawer.Category(group,cards,ClockStyles.labels.toList(),clockStyle) { clockStyle=it }
            else EditorDrawer.Category(group,cards)
        }+EditorDrawer.Category("Installed apps",listOf({
            val note=ui.column().apply { gravity=Gravity.CENTER }
            note.addView(EditorIcon(this,"add",ui.t.textSecondary),LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)))
            note.addView(ui.text("Widgets from apps installed on this unit",14,ui.t.textSecondary).apply { gravity=Gravity.CENTER;setPadding(ui.dp(12),ui.dp(8),ui.dp(12),0) })
            drawer.card(note,"Installed app widgets",listOf(EditorDrawer.Chip("Browse",primary=true) {
                widgetReplacement=index;drawer.dismiss();startActivityForResult(Intent(this,WidgetPickerActivity::class.java),720)
            }))
        }))
        val initial=WidgetCatalog.styles.firstOrNull { it.type==replacing?.type }?.let { s->groups.keys.indexOf(s.group) } ?: 0
        header()
        drawer.show(categories,initial,columns=2,rows=2)
    }
    /** Tab of the Home appearance panel (kept while it is open and between openings). */
    private var appearanceTab=0
    /** Rebuilds the open appearance panel after an edit made outside it (custom wallpaper picked, text entered). */
    private var appearanceFill:(()->Unit)?=null

    /**
     * Home appearance: a side panel beside the live preview, with tabs "Appearance" (theme, wallpaper, accent,
     * glass cards, dock) and "Vehicle & badges" (vehicle name and colour, trim badge and colour). Every choice edits
     * the editor's draft at once, so the preview shows it; the editor's tick saves it and Back discards it. Clock
     * styles are chosen per clock widget in Add widget; the 24-hour clock stays in Launcher settings.
     */
    private fun appearance(tab:Int=appearanceTab) {
        appearanceTab=tab
        val dialog=android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar)
        val root=ui.column()
        val scroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false }
        val body=ui.column()
        scroll.addView(body)
        lateinit var fill:()->Unit
        fun change(run:()->Unit) { edit(run);fill() }
        fill={
            // Rebuilt after every choice: keep the panel where it was scrolled.
            val y=scroll.scrollY
            scroll.post { scroll.scrollTo(0,y) }
            val t=ui.t
            root.background=android.graphics.drawable.GradientDrawable().apply { setColor(t.background);cornerRadius=ui.dp(20).toFloat();setStroke(ui.dp(1),t.cardBorder) }
            root.removeAllViews();body.removeAllViews()
            val head=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),ui.dp(12),ui.dp(12),ui.dp(8)) }
            head.addView(tabs(listOf("Appearance","Vehicle & badges"),appearanceTab) { appearanceTab=it;fill() },LinearLayout.LayoutParams(0,WRAP,1f))
            head.addView(control("back","Close appearance") { dialog.dismiss() }.apply { rotation=0f },LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
            root.addView(head)
            root.addView(scroll,LinearLayout.LayoutParams(MATCH,0,1f))
            if(appearanceTab==0) appearanceTab(body,::change) else vehicleTab(body,::change)
        }
        appearanceFill=fill
        fill()
        dialog.setContentView(root)
        dialog.setOnDismissListener { if(appearanceFill===fill) appearanceFill=null }
        dialog.show()
        dialog.window?.let { w->
            // Beside the preview, which stays visible and live.
            w.setLayout((resources.displayMetrics.widthPixels*.46f).toInt(),android.view.WindowManager.LayoutParams.MATCH_PARENT)
            w.setGravity(Gravity.END);w.setDimAmount(0f);w.immersive()
            w.attributes=w.attributes.apply { x=ui.dp(8);y=0 }
            w.decorView.setPadding(0,ui.dp(8),0,ui.dp(8))
        }
    }

    /** Pill tabs; [chosen] is selected. */
    private fun tabs(labels:List<String>,chosen:Int,pick:(Int)->Unit):LinearLayout {
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        labels.forEachIndexed { i,label->
            val on=i==chosen
            row.addView(ui.text(label,14,if(on) 0xffffffff.toInt() else ui.t.textPrimary).apply {
                gravity=Gravity.CENTER;isSingleLine=true;setPadding(ui.dp(14),0,ui.dp(14),0)
                background=android.graphics.drawable.GradientDrawable().apply { cornerRadius=ui.dp(18).toFloat();if(on) setColor(ui.t.accent) else { setColor(ui.t.card);setStroke(ui.dp(1),ui.t.cardBorder) } }
                contentDescription=if(on) "$label, selected" else label;isSelected=on
                setOnClickListener { if(!on) pick(i) }
            },LinearLayout.LayoutParams(WRAP,ui.dp(36)).apply { marginEnd=ui.dp(8) })
        }
        return row
    }

    private fun heading(body:LinearLayout,text:String) { body.addView(ui.section(text.uppercase(java.util.Locale.ROOT))) }

    /** Horizontal scroll offset of each thumbnail row, kept while the panel rebuilds after a choice. */
    private val thumbnailScroll=HashMap<String,Int>()

    /**
     * A horizontally scrolling row of thumbnails; [selected] has an accent ring and a check in its label. The row
     * stays where it was scrolled when the panel is rebuilt (choosing a theme updates the preview and the panel).
     */
    private fun thumbnails(body:LinearLayout,key:String,names:List<String>,selected:Int,drawable:(Int)->android.graphics.drawable.Drawable,overlay:(Int)->String?,pick:(Int)->Unit) {
        val row=LinearLayout(this).apply { setPadding(ui.dp(12),ui.dp(4),ui.dp(12),ui.dp(10)) }
        names.forEachIndexed { i,name->
            val on=i==selected
            val cell=ui.column().apply { gravity=Gravity.CENTER_HORIZONTAL;contentDescription=if(on) "$name, selected" else name;setOnClickListener { pick(i) } }
            val frame=FrameLayout(this).apply {
                background=drawable(i);clipToOutline=true
                outlineProvider=object:android.view.ViewOutlineProvider() { override fun getOutline(v:View,o:android.graphics.Outline) { o.setRoundRect(0,0,v.width,v.height,ui.dp(10).toFloat()) } }
                foreground=android.graphics.drawable.GradientDrawable().apply { setColor(0);cornerRadius=ui.dp(10).toFloat();setStroke(ui.dp(if(on) 3 else 1),if(on) ui.t.accent else ui.t.cardBorder) }
            }
            overlay(i)?.let { label-> frame.addView(ui.text(label,10,0xffffffff.toInt()).apply { gravity=Gravity.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD },FrameLayout.LayoutParams(MATCH,MATCH)) }
            cell.addView(frame,LinearLayout.LayoutParams(ui.dp(92),ui.dp(54)))
            cell.addView(ui.text(if(on) "✓ $name" else name,12,if(on) ui.t.textPrimary else ui.t.textSecondary).apply { gravity=Gravity.CENTER;maxLines=2;setPadding(0,ui.dp(4),0,0) },LinearLayout.LayoutParams(ui.dp(96),WRAP))
            row.addView(cell,LinearLayout.LayoutParams(WRAP,WRAP).apply { marginEnd=ui.dp(10) })
        }
        body.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false;addView(row)
            val saved=thumbnailScroll[key] ?: 0
            if(saved>0) viewTreeObserver.addOnPreDrawListener(object:android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw():Boolean { viewTreeObserver.removeOnPreDrawListener(this);scrollTo(saved,0);return true }
            })
            setOnScrollChangeListener { _,x,_,_,_-> thumbnailScroll[key]=x }
        })
    }

    /** Colour swatches (null = the theme's accent) plus "Custom…" (hex); the current one is ringed. */
    private fun swatches(body:LinearLayout,key:String,themeLabel:String,change:(()->Unit)->Unit) {
        val colors=listOf<Int?>(null,0xffe60012.toInt(),0xffffffff.toInt(),0xffffa040.toInt(),0xff36ca87.toInt(),0xff56a8ff.toInt(),0xffb184ff.toInt())
        val names=listOf(themeLabel,"Red","White","Orange","Green","Blue","Purple")
        val current=if(draft.contains(key)) draft.getInt(key,0) else null
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),ui.dp(4),ui.dp(12),ui.dp(10)) }
        colors.forEachIndexed { i,color->
            val on=color==current
            row.addView(View(this).apply {
                background=android.graphics.drawable.GradientDrawable().apply {
                    shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(color ?: ui.t.accent)
                    setStroke(ui.dp(if(on) 3 else 1),if(on) ui.t.textPrimary else ui.t.cardBorder)
                }
                contentDescription=if(on) "${names[i]}, selected" else names[i];tooltipText=names[i]
                setOnClickListener { change { if(color==null) draft.edit().remove(key).apply() else draft.edit().putInt(key,color).apply() } }
            },LinearLayout.LayoutParams(ui.dp(if(on) 38 else 34),ui.dp(if(on) 38 else 34)).apply { marginEnd=ui.dp(10) })
        }
        val custom=current!=null && current !in colors
        row.addView(ui.text(if(custom) "Custom ✓ #%06X".format(current!! and 0xffffff) else "Custom…",13,ui.t.textPrimary).apply {
            gravity=Gravity.CENTER;isSingleLine=true;setPadding(ui.dp(12),0,ui.dp(12),0)
            background=android.graphics.drawable.GradientDrawable().apply { cornerRadius=ui.dp(16).toFloat();setColor(ui.t.card);setStroke(ui.dp(if(custom) 2 else 1),if(custom) ui.t.accent else ui.t.cardBorder) }
            contentDescription="Custom color (hex)";setOnClickListener { color(key) }
        },LinearLayout.LayoutParams(WRAP,ui.dp(34)))
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(row) })
    }

    private fun appearanceTab(body:LinearLayout,change:(()->Unit)->Unit) {
        val group={ ui.group().also { body.addView(it) } }
        heading(body,"Mode")
        val modeRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),ui.dp(2),ui.dp(12),ui.dp(8)) }
        val light=ThemeTokens.NAMES.indexOf(draft.getString(Keys.THEME,ThemeTokens.NAMES[0])).coerceAtLeast(0)>=5
        modeRow.addView(tabs(listOf("Dark","Light"),if(light) 1 else 0) { i-> change { ThemeTokens.chooseMode(previewContext,if(i==1) "light" else "dark") } })
        body.addView(modeRow)
        heading(body,"Theme")
        val themeIndex=ThemeTokens.NAMES.indexOf(draft.getString(Keys.THEME,ThemeTokens.NAMES[0])).coerceAtLeast(0)
        thumbnails(body,"theme",ThemeTokens.NAMES.toList(),themeIndex,{ i->
            android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(ThemeTokens.VALUES[i][0]),
                android.graphics.drawable.GradientDrawable().apply { setColor(ThemeTokens.VALUES[i][1]);cornerRadius=ui.dp(5).toFloat() })).apply {
                setLayerInset(1,ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12)) }
        },{ null }) { i-> change { ThemeTokens.select(previewContext,i) } }
        heading(body,"Wallpaper")
        val wallpaper=WallpaperPack.index(previewContext,false)
        // Gallery order ("Custom image" last); positions map back to stored indexes.
        val gallery=WallpaperPack.GALLERY
        thumbnails(body,"wallpaper",gallery.map { WallpaperPack.HOME_NAMES[it] },gallery.indexOf(wallpaper),{ p->WallpaperPack.preview(previewContext,ui.t,false,gallery[p]) },
            { p->if(gallery[p]==WallpaperPack.CUSTOM_INDEX && wallpaper!=gallery[p]) "CUSTOM" else null }) { p->
            val i=gallery[p]
            if(i==WallpaperPack.CUSTOM_INDEX) CustomImages.choose(this) else change { WallpaperPack.select(previewContext,"home",i) }
        }
        val walls=group()
        ui.add(walls,ui.toggle("Flip wallpaper",draft.getBoolean(Keys.FLIP_GRADIENTS,false),Runnable { change { draft.edit().putBoolean(Keys.FLIP_GRADIENTS,!draft.getBoolean(Keys.FLIP_GRADIENTS,false)).apply() } }))
        ui.add(walls,ui.toggle("Glass cards",draft.glassCards,Runnable { change { draft.edit().putBoolean(Keys.GLASS_CARDS,!draft.glassCards).apply() } }))
        heading(body,"Accent color")
        swatches(body,Keys.ACCENT_COLOR,"Theme default",change)
        heading(body,"Dock")
        val dock=group()
        val sides=listOf("bottom","left","right")
        val placeRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(56);setPadding(ui.dp(16),0,ui.dp(12),0) }
        placeRow.addView(ui.text("Placement",16,ui.t.textPrimary),LinearLayout.LayoutParams(0,WRAP,1f))
        placeRow.addView(tabs(listOf("Bottom","Left","Right"),sides.indexOf(HomeChrome.dockSide(draft))) { i-> change { draft.edit().putString(Keys.DOCK_POSITION,sides[i]).apply() } })
        ui.add(dock,placeRow)
        ui.add(dock,ui.text("Left and right docks show the clock, status and the first three shortcuts, without labels. Labels and icon size apply to the bottom dock; extra shortcuts stay saved for it.",12,ui.t.textSecondary).apply { setPadding(ui.dp(16),0,ui.dp(16),ui.dp(10)) })
        // Side docks show icons only; this choice applies to the bottom dock.
        ui.add(dock,ui.toggle("Labels under icons",draft.getBoolean(Keys.DOCK_LABELS,true),Runnable { change { draft.edit().putBoolean(Keys.DOCK_LABELS,!draft.getBoolean(Keys.DOCK_LABELS,true)).apply() } }))
        val size=DockConfig.ICON_SIZES.indexOf(DockConfig.iconCeiling(draft.getInt(Keys.DOCK_ICONS,24)))
        val sizeRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(56);setPadding(ui.dp(16),0,ui.dp(12),0) }
        sizeRow.addView(ui.text("Icon size",16,ui.t.textPrimary),LinearLayout.LayoutParams(0,WRAP,1f))
        sizeRow.addView(tabs(listOf("Small","Medium","Large"),size) { i-> change { draft.edit().putInt(Keys.DOCK_ICONS,DockConfig.ICON_SIZES[i]).apply() } })
        ui.add(dock,sizeRow)
        body.addView(ui.text("App drawer and screensaver wallpapers stay in Launcher settings.",12,ui.t.textSecondary).apply { setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(16)) })
    }

    private fun vehicleTab(body:LinearLayout,change:(()->Unit)->Unit) {
        heading(body,"Vehicle")
        val vehicle=ui.group().also { body.addView(it) }
        ui.add(vehicle,ui.valueRow("Vehicle name",draft.getString(Keys.BRAND_TEXT,Defaults.VEHICLE_NAME).orEmpty().ifBlank { "Hidden" },
            Runnable { text("Vehicle name",Keys.BRAND_TEXT,Defaults.VEHICLE_NAME) }))
        body.addView(ui.text("Vehicle name color",13,ui.t.textSecondary).apply { setPadding(ui.dp(16),ui.dp(8),0,0) })
        swatches(body,Keys.BRAND_COLOR,"Accent",change)
        heading(body,"Trim badge")
        val badge=ui.group().also { body.addView(it) }
        ui.add(badge,ui.valueRow("Trim text",draft.getString(Keys.BADGE_TEXT,Defaults.TRIM_BADGE).orEmpty().ifBlank { "Hidden" },
            Runnable { text("Trim text",Keys.BADGE_TEXT,Defaults.TRIM_BADGE) }))
        body.addView(ui.text("Trim badge color",13,ui.t.textSecondary).apply { setPadding(ui.dp(16),ui.dp(8),0,0) })
        swatches(body,Keys.BADGE_COLOR,"Accent",change)
        body.addView(ui.text("Shown on the clock widgets that carry the vehicle name and badge.",12,ui.t.textSecondary).apply { setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(16)) })
    }
    private fun text(title:String,key:String,default:String) {
        val input=EditText(this).apply { setSingleLine();setText(draft.getString(key,default));setTextColor(ui.t.textPrimary) }
        ThemedDialogs.Builder(this).setTitle(title).setView(input).setPositiveButton("Apply") { _,_->edit { draft.edit().putString(key,input.text.toString().trim()).apply() };appearanceFill?.invoke() }.setNegativeButton("Cancel",null).show()
    }
    private fun color(key:String) {
        val input=EditText(this).apply { hint="#RRGGBB";setSingleLine();setTextColor(ui.t.textPrimary) }
        ThemedDialogs.Builder(this).setTitle("Color").setView(input).setPositiveButton("Apply") { _,_->
            try { val value=android.graphics.Color.parseColor(input.text.toString().trim());edit { draft.edit().putInt(key,value).apply() };appearanceFill?.invoke() } catch(_:IllegalArgumentException) { Toast.makeText(this,"Enter a color such as #E60012",Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Cancel",null).show()
    }
    private fun cornerName(left:Boolean)=when(draft.getString(if(left) Keys.WIDGET_LEFT else Keys.WIDGET_RIGHT,if(left) "auto" else "trim")) {
        "sun"->"Sunrise / sunset";"trim"->"Trim badge";"off"->"None";else->"Weather"
    }
    private fun corner(left:Boolean) { ThemedDialogs.Builder(this).setTitle(if(left) "Top left" else "Top right").setItems(arrayOf("Weather","Sunrise / sunset","Trim badge","None")) { _,i->edit { draft.edit().putString(if(left) Keys.WIDGET_LEFT else Keys.WIDGET_RIGHT,arrayOf("auto","sun","trim","off")[i]).apply() } }.show() }
    private fun dockAppearance() = appearance(0)
    private fun chooseApp(key:String,dock:Int=-1) { pendingKey=key;pendingDock=dock;startActivityForResult(Intent(this,AppPickerActivity::class.java).putExtra(AppPickerActivity.ROLE,"editor"),610) }
    private fun apps() {
        val apps=JSONArray(draft.getString(Keys.LAYOUT_APPS,"[]"));val names=Array(apps.length()+1) { i->if(i==0) "Add application" else "Remove "+apps.optString(i).substringBefore('/') }
        ThemedDialogs.Builder(this).setTitle("Applications").setItems(names) { _,i->if(i==0) chooseApp(Keys.LAYOUT_APPS) else edit { apps.remove(i-1);draft.edit().putString(Keys.LAYOUT_APPS,apps.toString()).apply() } }.show()
    }
    @Deprecated("Deprecated in Android") override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(request==720 && result==RESULT_OK) {
            val id=data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,-1) ?: -1
            if(id<=0) return
            allocatedWidgets.add(id)
            if(page !in pages.indices) page=pages.lastIndex
            val others=pages[page].filterIndexed { n,_->n!=widgetReplacement }
            val fit=WidgetCatalog.fit(others,"widget:"+id,4,3)
            if(fit!=null) addChosen(fit,widgetReplacement)
            else if(pages.size<5) { pages.add(mutableListOf());page=pages.lastIndex;addChosen(GridTile("widget:"+id,0,0,4,3),-1) }
            else Toast.makeText(this,"Make space for a 4×3 widget and try again",Toast.LENGTH_LONG).show()
            return
        }
        if(request==CustomImages.PICK && result==RESULT_OK && data?.data!=null) {
            val name=java.util.UUID.randomUUID().toString()
            CustomImages.importTo(this,data.data!!,"home",java.io.File(images,name)) { edit { draft.edit().putString(Keys.EDITOR_IMAGE,name).apply();WallpaperPack.select(previewContext,"home",WallpaperPack.CUSTOM_INDEX) };appearanceFill?.invoke() };return
        }
        if(request!=610 || result!=RESULT_OK) return
        val target=data?.getStringExtra(AppPickerActivity.TARGET).orEmpty();if(target.isEmpty()) return
        edit { when {
            pendingDock>=0 && pendingInsert->{ val items=DockConfig.read(draft)
                items.add(pendingDock.coerceIn(0,items.size),DockConfig.Item(DockAction.APP,target,true));DockConfig.save(draft,items)
                pendingDock=-1;pendingInsert=false }
            pendingDock>=0->{ val items=DockConfig.read(draft)
                if(pendingDock in items.indices) {
                    // CarPlay / Android Auto keep their icon and name, linked to the chosen app.
                    val keep=items[pendingDock].action.takeIf { pendingLink && it.linkable }
                    items[pendingDock]=DockConfig.Item(keep ?: DockAction.APP,target,true);DockConfig.save(draft,items)
                }
                pendingDock=-1;pendingLink=false }
            pendingKey==Keys.LAYOUT_APPS->{ val a=JSONArray(draft.getString(Keys.LAYOUT_APPS,"[]"));if(a.length()<LayoutPrefs.MAX_APP_SHORTCUTS && (0 until a.length()).none { a.optString(it)==target }) a.put(target);draft.edit().putString(Keys.LAYOUT_APPS,a.toString()).apply() }
            else->draft.edit().putString(pendingKey,target).apply()
        } }
    }
    override fun onResume() { super.onResume();mapCards.forEach { it.start() } }
    override fun onPause() { mapCards.forEach { it.stop() };super.onPause() }
    override fun onDestroy() {
        if(isFinishing && ::draft.isInitialized) {
            val kept=HostedWidgets.ids(ProfileStore.preferences(this).getString(DashboardPages.KEY,null))
            (allocatedWidgets-kept).forEach { HostedWidgets.delete(this,it) }
        }
        HostedWidgets.destroy(this)
        if(isFinishing && ::images.isInitialized) { images.listFiles()?.forEach { it.delete() };images.delete() }
        super.onDestroy()
    }
    private fun presets() {
        val layouts=listOf(
            "Balanced" to listOf(GridTile("clock",0,0,4,6),GridTile("media",4,0,4,6)),
            "Media" to listOf(GridTile("media_wide",0,0,8,4),GridTile("clock_minimal",0,4,4,2),GridTile("projection",4,4,4,2)),
            "Minimal" to listOf(GridTile("clock_minimal",0,0,8,6)),
            "Three cards · clock left" to listOf(GridTile("clock",0,0,4,6),GridTile("media_wide",4,0,4,3),GridTile("apps",4,3,4,3)),
            "Three cards · clock right" to listOf(GridTile("media_wide",0,0,4,3),GridTile("apps",0,3,4,3),GridTile("clock",4,0,4,6)),
            "Three cards · media left" to listOf(GridTile("media",0,0,4,6),GridTile("clock_only",4,0,4,3),GridTile("projection",4,3,4,3)),
            "Three cards · media right" to listOf(GridTile("clock_only",0,0,4,3),GridTile("projection",0,3,4,3),GridTile("media",4,0,4,6)),
            "Three cards · clock above" to listOf(GridTile("clock_minimal",0,0,8,3),GridTile("media_wide",0,3,4,3),GridTile("apps",4,3,4,3)),
            "Three cards · columns" to listOf(GridTile("clock_minimal",0,0,2,6),GridTile("media",2,0,3,6),GridTile("apps",5,0,3,6)),
            "Map left · media + clock" to listOf(GridTile("offline_map",0,0,5,6),GridTile("media_wide",5,0,3,3),GridTile("clock_minimal",5,3,3,3)),
            "Map right · clock + media" to listOf(GridTile("clock_minimal",0,0,3,3),GridTile("media_wide",0,3,3,3),GridTile("offline_map",3,0,5,6)),
            "Map above · media + clock" to listOf(GridTile("offline_map",0,0,8,4),GridTile("media_compact",0,4,4,2),GridTile("clock_minimal",4,4,4,2)))
        // Grouped like the widget drawer: tabs on the left, a page of previews on the right.
        val groups=listOf("Classic" to layouts.take(3),"Three cards" to layouts.subList(3,9),"With map" to layouts.subList(9,12))
        val drawer=EditorDrawer(this,ui,"Ready-made layouts")
        val categories=groups.map { (group,items)->
            EditorDrawer.Category(group,items.map { (name,tiles)-> {
                val board=GridBoard(this,tiles.toMutableList());tiles.forEach { board.addView(sample(it.type,it.w,it.h)) }
                // Inside a tab the group is already named: "Three cards · clock left" shows as "Clock left".
                val title=if(group=="Three cards") name.substringAfter("· ").replaceFirstChar { it.uppercase() } else name
                drawer.card(board,title,listOf(EditorDrawer.Chip("Use this layout",primary=true) {
                    edit { pages[page]=tiles.toMutableList();selected=-1 };drawer.dismiss()
                }))
            } })
        }
        drawer.show(categories,0,columns=3,rows=2)
    }
    /** "Reset page": clock and the default media card (Album player). */
    private fun defaultLayout()=mutableListOf(GridTile("clock",0,0,4,6),GridTile("media_album",4,0,4,6))
    private fun movePage(direction:Int) {
        val next=page+direction
        if(page !in pages.indices || next !in pages.indices) return
        edit {
            java.util.Collections.swap(pages,page,next)
            if(defaultPage==page) defaultPage=next else if(defaultPage==next) defaultPage=page
            page=next;selected=-1;dockSelected=-1;placing=null
        }
    }
    private fun dragPage(distance:Float,end:Boolean,cancel:Boolean) {
        if(page !in pages.indices || placing!=null) return
        val threshold=(preview.height*.24f).coerceIn(ui.dp(48).toFloat(),ui.dp(96).toFloat())
        val dy=distance.coerceIn(0f,threshold*1.5f)
        if(!end) {
            deleteDragging=true;screen.animate().cancel();trash.animate().cancel()
            screen.translationY=previewY+dy
            trash.alpha=(dy/(threshold*.5f)).coerceIn(0f,1f)
            trash.translationY=-ui.dp(40)*(1-trash.alpha)
            trash.armed=dy>=threshold
            return
        }
        deleteDragging=false
        val duration=if(Motion.enabled(previewContext)) 180L else 0L
        screen.animate().translationY(previewY).setDuration(duration).start()
        trash.animate().alpha(0f).translationY(-ui.dp(40).toFloat()).setDuration(duration).start()
        if(cancel || dy<threshold) return
        if(pages.size==1) { Toast.makeText(this,"Keep at least one Home page",Toast.LENGTH_SHORT).show();return }
        val target=page
        ThemedDialogs.Builder(this).setTitle("Delete page ${target+1}?").setMessage("Remove this page and its widgets? Your dock will be kept. Changes apply only when you save.")
            .setPositiveButton("Delete") { _,_->edit {
                pages.removeAt(target)
                if(defaultPage>target) defaultPage--
                defaultPage=defaultPage.coerceAtMost(pages.lastIndex);page=target.coerceAtMost(pages.lastIndex)
                selected=-1;dockSelected=-1;placing=null
            } }.setNegativeButton("Cancel",null).show()
    }
    /**
     * Dock shortcut menu: change it (application or built-in action), link CarPlay / Android Auto to an installed app,
     * add a shortcut (up to [DockConfig.MAX_ITEMS] shown) or remove it. The app-drawer slot stays.
     */
    private fun dockMenu(index:Int) {
        val items=DockConfig.read(draft);val item=items.getOrNull(index) ?: return
        val shown=items.count { it.visible }
        // A side dock shows three shortcuts; more stay saved for the bottom dock but cannot be added from here.
        val side=HomeChrome.dockSide(draft)!="bottom"
        val sideShown=DockConfig.sideShortcuts(items).size
        val actions=ArrayList<Pair<String,()->Unit>>()
        if(item.action!=DockAction.APPS) {
            actions.add("Choose application" to { chooseApp("",index) })
            actions.add("Choose built-in action" to { builtInAction { a-> edit { items[index]=DockConfig.Item(a,"",true);DockConfig.save(draft,items) } } })
            if(item.action.linkable) {
                actions.add((if(item.target.isEmpty()) "Link to an installed app" else "Linked: ${AppLookup.label(this,item.target) ?: "app unavailable"} · change") to { pendingLink=true;chooseApp("",index) })
                if(item.target.isNotEmpty()) actions.add("Use the default app" to { edit { items[index]=DockConfig.Item(item.action,"",true);DockConfig.save(draft,items) } })
            }
        }
        if(if(side) sideShown<DockConfig.SIDE_SHORTCUTS && shown<DockConfig.MAX_ITEMS else shown<DockConfig.MAX_ITEMS)
            actions.add((if(side) "Add a shortcut ($sideShown of ${DockConfig.SIDE_SHORTCUTS})" else "Add a shortcut (${shown} of ${DockConfig.MAX_ITEMS})") to {
            builtInAction(withApp=true) { a-> edit { items.add(index+1,DockConfig.Item(a,"",true));DockConfig.save(draft,items) } }
        })
        if(item.action!=DockAction.APPS && shown>2) actions.add("Remove from dock" to { edit { items.removeAt(index);DockConfig.save(draft,items);dockSelected=-1 } })
        actions.add("Dock appearance" to { appearance(0) })
        ThemedDialogs.Builder(this).setTitle(DockConfig.name(this,item)).setItems(actions.map { it.first }.toTypedArray()) { _,choice->actions[choice].second() }.show()
    }

    /** Picks a built-in dock action; [withApp] also offers "Application" (then the app picker for a new slot). */
    private fun builtInAction(withApp:Boolean=false,chosen:(DockAction)->Unit) {
        val list=DockAction.ASSIGNABLE
        val names=(if(withApp) listOf("Application…") else emptyList())+list.map { it.label }
        ThemedDialogs.Builder(this).setTitle("Shortcut action").setItems(names.toTypedArray()) { _,i->
            // A new application slot is added only once an app is picked (cancelling adds nothing).
            if(withApp && i==0) { pendingInsert=true;chooseApp("",(dockSelected+1).coerceIn(0,DockConfig.read(draft).size)) }
            else chosen(list[i-(if(withApp) 1 else 0)])
        }.show()
    }
}
