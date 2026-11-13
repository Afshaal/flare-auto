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
    private fun command(row:LinearLayout,label:String,run:()->Unit) {
        row.addView(ui.text(label,13,ui.t.textPrimary).apply {
            gravity=Gravity.CENTER;minimumHeight=ui.dp(44)
            background=android.graphics.drawable.GradientDrawable().apply { setColor(ui.t.card);cornerRadius=ui.dp(14).toFloat();setStroke(ui.dp(1),ui.t.cardBorder) }
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
            contentDescription=label;isEnabled=enabled;alpha=if(enabled) 1f else .25f
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
        val spacing=homeDp(draft.getInt(Keys.SPACING,14));screen.setPadding(spacing,homeDp(10),spacing,homeDp(10))
        val top=HomeChrome.TopBar(previewContext,draft,ui.t)
        top.date.text=java.text.SimpleDateFormat("EEE, MMM d",java.util.Locale.getDefault()).format(java.util.Date())
        screen.addView(top);screen.addView(View(previewContext).apply { background=HomeChrome.accent(ui.t) },LinearLayout.LayoutParams(MATCH,homeDp(1)))
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
                display.forEach { tile->val cell=LinearLayout(previewContext);content.build(cell,tile.type,tile.h<5);board.addView(CardFrame(previewContext,cell,tile.type)) }
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
        screen.addView(strip,LinearLayout.LayoutParams(MATCH,0,1f).apply { topMargin=homeDp(12);bottomMargin=homeDp(12) })
        if(pages.size>1) screen.addView(TextView(previewContext).apply {
            text=pages.indices.joinToString("  ") { if(it==page) "●" else "○" }
            textSize=13*draft.getInt(Keys.TEXT,100)/100f;setTextColor(ui.t.textSecondary);gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(MATCH,homeDp(28)))
        val dock=HomeChrome.dock(previewContext,draft,ui.t) { index,_->
            selected=-1;activeBoard?.let { it.selection=-1;it.invalidate() };dockSelected=index;contextPanel();dockMenu(index)
        }
        EditorDockDrag(dock,{ screen.scaleX },{ from,to->
            val items=DockConfig.read(draft);val item=items.removeAt(from);items.add(to,item);DockConfig.save(draft,items)
        },{ render() })
        screen.addView(dock,LinearLayout.LayoutParams(MATCH,homeDp(draft.getInt(Keys.DOCK,70))))
        val real=page in pages.indices
        fun rail(side:Int):LinearLayout=ui.column().apply {
            gravity=Gravity.CENTER_HORIZONTAL;setPadding(0,ui.dp(4),0,ui.dp(4));setOnClickListener { clearSelection() }
            stage.addView(this,FrameLayout.LayoutParams(ui.dp(68),MATCH,side))
        }
        val left=rail(Gravity.LEFT)
        railButton(left,"back","Back") { finish() }
        left.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        railButton(left,"layouts","Layouts",real) { presets() }
        railButton(left,"main","Main page",real,real && page==defaultPage) { if(defaultPage!=page) edit { defaultPage=page } }
        railButton(left,"reset","Reset page",real) { ThemedDialogs.Builder(this).setTitle("Reset this page?").setMessage("Restore the default clock and media cards on this page. Other pages and the dock stay as they are.")
            .setPositiveButton("Reset") { _,_->edit { pages[page]=defaultLayout();selected=-1;placing=null } }.setNegativeButton("Cancel",null).show() }
        left.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        railButton(left,"left","Move page left",real && page>0) { movePage(-1) }
        val right=rail(Gravity.RIGHT)
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
        if(tile==null) return
        command(panel,"Change widget") { gallery(selected) }
        if(tile.type.startsWith("clock") && tile.type!="clock_date") { command(panel,"Clock & vehicle") { clockOptions() };command(panel,"Corners") { ThemedDialogs.Builder(this).setTitle("Clock corner").setItems(arrayOf("Top left","Top right")) { _,i->corner(i==0) }.show() } }
        if(tile.type=="apps") command(panel,"Applications") { apps() }
        if(HostedWidgets.id(tile.type)!=null) command(panel,"Widget settings") {
            try { HostedWidgets.host(this).startAppWidgetConfigureActivityForResult(this,HostedWidgets.id(tile.type)!!,0,721,null) }
            catch(_:RuntimeException) { Toast.makeText(this,"This widget has no configuration screen",Toast.LENGTH_SHORT).show() }
        }
        if(tile.type=="projection" || tile.type=="clock") command(panel,"Projection apps") { ThemedDialogs.Builder(this).setTitle("Projection apps").setItems(arrayOf("CarPlay","Android Auto")) { _,i->chooseApp(if(i==0) Keys.APP_CARPLAY else Keys.APP_ANDROIDAUTO) }.show() }
        command(panel,"Remove") { edit { pages[page].removeAt(selected);selected=-1 } }
    }
    private fun sample(type:String,w:Int,h:Int):View {
        val cell=LinearLayout(this);val renderer=HomeCards(this,draft,ui.t,preview=true,sampleData=true,context=previewContext,duration={180000})
        renderer.build(cell,type,h<5);renderer.trackTitle?.text="Midnight Drive";renderer.trackArtist?.text="Sample artist"
        renderer.albumArt?.setImageDrawable(MediaGlyph("note",ui.t.accent));renderer.elapsed?.text="1:24";renderer.total?.text="3:00"
        return CardFrame(this,cell,type)
    }
    /**
     * "Add widget" / "Change widget": a full-screen drawer with the widget groups as tabs and the styles as cards
     * (live sample, name, the sizes that fit on this page, "New page"). Choosing a size places the widget at once
     * and selects it ([addChosen]); installed app widgets have their own tab.
     */
    private fun gallery(index:Int) {
        if(page !in pages.indices) { Toast.makeText(this,"Tap + to create a page first",Toast.LENGTH_SHORT).show();return }
        val drawer=EditorDrawer(this,ui,if(index>=0) "Change widget" else "Add widget")
        fun choose(style:WidgetCatalog.Style,w:Int,h:Int) {
            val others=pages[page].filterIndexed { n,_->n!=index }
            val old=pages[page].getOrNull(index)
            val atOld=old?.copy(type=style.type,w=w,h=h)?.takeIf { it.valid() && others.none { t->t.overlaps(it) || t.type==it.type } }
            val fit=atOld ?: WidgetCatalog.fit(others,style.type,w,h)
            if(fit!=null) { drawer.dismiss();addChosen(fit,index) }
            else ThemedDialogs.Builder(this).setTitle("No space for this size").setMessage("Choose a smaller size, resize another widget, or add a new page.")
                .setPositiveButton("New page") { _,_->if(pages.size<5) { pages.add(mutableListOf());page=pages.lastIndex;addChosen(GridTile(style.type,0,0,w,h),-1);drawer.dismiss() } }
                .setNegativeButton("Back",null).show()
        }
        fun newPage(style:WidgetCatalog.Style) {
            if(pages.size>=5) { Toast.makeText(this,"Five pages maximum",Toast.LENGTH_SHORT).show();return }
            val size=style.sizes.first()
            pages.add(mutableListOf());page=pages.lastIndex;addChosen(GridTile(style.type,0,0,size.first,size.second),-1)
            drawer.dismiss()
        }
        val others=pages[page].filterIndexed { n,_->n!=index }
        val groups=WidgetCatalog.styles.groupBy { it.group }
        val categories=groups.map { (group,styles)->
            EditorDrawer.Category(group,styles.map { style-> {
                val size=style.sizes.first()
                val chips=style.sizes.map { (w,h)->
                    val valid=GridTile(style.type,0,0,w,h).valid() && WidgetCatalog.fit(others,style.type,w,h)!=null
                    EditorDrawer.Chip("${w}×${h}",valid,primary=valid) { choose(style,w,h) }
                }+EditorDrawer.Chip("+ Page",pages.size<5) { newPage(style) }
                drawer.card(sample(style.type,size.first,size.second),style.name,chips)
            } })
        }+EditorDrawer.Category("Installed apps",listOf({
            val note=ui.column().apply { gravity=Gravity.CENTER }
            note.addView(EditorIcon(this,"add",ui.t.textSecondary),LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)))
            note.addView(ui.text("Widgets from apps installed on this unit",14,ui.t.textSecondary).apply { gravity=Gravity.CENTER;setPadding(ui.dp(12),ui.dp(8),ui.dp(12),0) })
            drawer.card(note,"Installed app widgets",listOf(EditorDrawer.Chip("Browse",primary=true) {
                widgetReplacement=index;drawer.dismiss();startActivityForResult(Intent(this,WidgetPickerActivity::class.java),720)
            }))
        }))
        val current=pages[page].getOrNull(index)?.type
        val initial=WidgetCatalog.styles.firstOrNull { it.type==current }?.let { s->groups.keys.indexOf(s.group) } ?: 0
        drawer.show(categories,initial,columns=2,rows=2)
    }
    private fun appearance() {
        ThemedDialogs.Builder(this).setTitle("Home appearance").setItems(arrayOf("Theme","Wallpaper","Accent color","Vehicle name color","Badge color","Clock & vehicle","Dock appearance")) { _,i->when(i) {
            0->ThemedDialogs.Builder(this).setTitle("Theme").setItems(ThemeTokens.NAMES) { _,n->edit { ThemeTokens.select(previewContext,n) } }.show()
            1->ThemedDialogs.Builder(this).setTitle("Wallpaper").setItems(WallpaperPack.HOME_NAMES) { _,n->if(n==WallpaperPack.CUSTOM_INDEX) CustomImages.choose(this) else edit { WallpaperPack.select(previewContext,"home",n) } }.show()
            2->color(Keys.ACCENT_COLOR);3->color(Keys.BRAND_COLOR);4->color(Keys.BADGE_COLOR);5->clockOptions();else->dockAppearance()
        } }.show()
    }
    private fun text(title:String,key:String,default:String) {
        val input=EditText(this).apply { setSingleLine();setText(draft.getString(key,default));setTextColor(ui.t.textPrimary) }
        ThemedDialogs.Builder(this).setTitle(title).setView(input).setPositiveButton("Apply") { _,_->edit { draft.edit().putString(key,input.text.toString().trim()).apply() } }.setNegativeButton("Cancel",null).show()
    }
    private fun color(key:String) {
        val input=EditText(this).apply { hint="#RRGGBB";setSingleLine();setTextColor(ui.t.textPrimary) }
        ThemedDialogs.Builder(this).setTitle("Color").setView(input).setPositiveButton("Apply") { _,_->
            try { val value=android.graphics.Color.parseColor(input.text.toString().trim());edit { draft.edit().putInt(key,value).apply() } } catch(_:IllegalArgumentException) { Toast.makeText(this,"Enter a color such as #E60012",Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Cancel",null).show()
    }
    private fun clockOptions() { ThemedDialogs.Builder(this).setTitle("Clock & vehicle").setItems(arrayOf("Clock style","Vehicle name","Vehicle color","Trim text","Trim color","Toggle 24-hour clock")) { _,i->when(i) {
        0->ThemedDialogs.Builder(this).setTitle("Clock style").setItems(ClockStyles.labels) { _,n->edit { draft.edit().putString(Keys.HOME_CLOCK_STYLE,ClockStyles.ids[n]).apply() } }.show()
        1->text("Vehicle name",Keys.BRAND_TEXT,Defaults.VEHICLE_NAME);2->color(Keys.BRAND_COLOR);3->text("Trim text",Keys.BADGE_TEXT,Defaults.TRIM_BADGE);4->color(Keys.BADGE_COLOR);5->edit { draft.edit().putBoolean(Keys.CLOCK24,!draft.getBoolean(Keys.CLOCK24,false)).apply() }
    } }.show() }
    private fun corner(left:Boolean) { ThemedDialogs.Builder(this).setTitle(if(left) "Top left" else "Top right").setItems(arrayOf("Weather","Sunrise / sunset","Trim badge","None")) { _,i->edit { draft.edit().putString(if(left) Keys.WIDGET_LEFT else Keys.WIDGET_RIGHT,arrayOf("auto","sun","trim","off")[i]).apply() } }.show() }
    private fun dockAppearance() { ThemedDialogs.Builder(this).setTitle("Dock").setItems(arrayOf("Toggle labels","Small icons","Medium icons","Large icons")) { _,i->edit {
        val e=draft.edit();if(i==0) e.putBoolean(Keys.DOCK_LABELS,!draft.getBoolean(Keys.DOCK_LABELS,true)) else e.putInt(Keys.DOCK_ICONS,DockConfig.ICON_SIZES[i-1]);e.apply()
    } }.show() }
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
            CustomImages.importTo(this,data.data!!,"home",java.io.File(images,name)) { edit { draft.edit().putString(Keys.EDITOR_IMAGE,name).apply();WallpaperPack.select(previewContext,"home",WallpaperPack.CUSTOM_INDEX) } };return
        }
        if(request!=610 || result!=RESULT_OK) return
        val target=data?.getStringExtra(AppPickerActivity.TARGET).orEmpty();if(target.isEmpty()) return
        edit { when {
            pendingDock>=0->{ val items=DockConfig.read(draft);if(pendingDock in items.indices) { items[pendingDock]=DockConfig.Item(DockAction.APP,target,true);DockConfig.save(draft,items) };pendingDock=-1 }
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
    private fun defaultLayout()=mutableListOf(GridTile("clock",0,0,4,6),GridTile("media",4,0,4,6))
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
    private fun dockMenu(index:Int) {
        val items=DockConfig.read(draft);val item=items.getOrNull(index) ?: return
        val labels=if(item.action==DockAction.APPS) arrayOf("Dock appearance") else arrayOf("Choose application","Choose built-in action","Dock appearance")
        ThemedDialogs.Builder(this).setTitle(DockConfig.name(this,item)).setItems(labels) { _,choice->when {
            item.action==DockAction.APPS || choice==2->dockAppearance()
            choice==0->chooseApp("",index)
            else->ThemedDialogs.Builder(this).setTitle("Shortcut action").setItems(DockAction.ASSIGNABLE.map { it.label }.toTypedArray()) { _,type->
                edit { items[index]=DockConfig.Item(DockAction.ASSIGNABLE[type],"",true);DockConfig.save(draft,items) }
            }.show()
        } }.show()
    }
}
