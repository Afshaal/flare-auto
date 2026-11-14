package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.location.LocationManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

internal class OfflineMapPanel(private val a:Activity,private val ui:UiKit,private val group:LinearLayout):OfflineMapStore.Listener {
    private val p=ProfileStore.preferences(a)
    private val refreshers=ArrayList<()->Unit>()
    /** Navigation pack state, checked on the routing thread whenever settings resume. */
    private var pack:NavigationPack.Status?=null
    /** Installed pack folders and their status (without hashing), read on the routing thread in [resume]. */
    private var packs:List<Pair<java.io.File,NavigationPack.Status>> = emptyList()
    /** Why this unit cannot run MapLibre, or null. */
    private val rendererProblem=VectorMapView.unsupportedReason(a)
    /**
     * Offline maps settings, kept to the controls a driver uses: on/off, the navigation pack (choice of installed
     * packs), the map renderer ("MapLibre · 3D" or "Mapsforge · 2D", stored in [Keys.OFFLINE_MAP_VECTOR]) with the
     * controls of the renderer in use only (orientation for MapLibre; "Other regions" for Mapsforge, which gathers
     * the region actions: catalog, URL, download, import, remove), then the shared following and map view controls.
     * Attribution lives in About › Open-source credits (with the installed pack's sources).
     */
    init {
        val enabled=ui.toggle("Enable offline maps",p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false),Runnable {
            p.edit().putBoolean(Keys.OFFLINE_MAP_ENABLED,!p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false)).apply();refresh()
        })
        ui.add(group,enabled);refreshers.add { enabled.switch.isChecked=p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false) }
        fun row(name:String,value:()->String,action:()->Unit):android.view.View {
            val v=ui.valueRow(name,value(),Runnable(action));ui.add(group,v);refreshers.add { v.setValue(value()) };return v
        }
        fun toggle(name:String,key:String) {
            val t=ui.toggle(name,p.getBoolean(key,true),Runnable { p.edit().putBoolean(key,!p.getBoolean(key,true)).apply();refresh() })
            ui.add(group,t);refreshers.add { t.switch.isChecked=p.getBoolean(key,true) }
        }
        /** Shows or hides a row together with the divider above it. */
        fun visible(row:android.view.View,show:Boolean) {
            val state=if(show) android.view.View.VISIBLE else android.view.View.GONE
            row.visibility=state
            group.getChildAt(group.indexOfChild(row)-1)?.takeIf { it.tag==UiKit.Role.DIVIDER }?.visibility=state
        }
        row("Navigation pack",{ packValue() }) { choosePack() }
        // A pack ZIP from Downloads or USB, extracted and verified in the app (PackImport).
        row("Import navigation pack",{ PackImport.status.ifBlank { "ZIP from Downloads or USB" } }) { importPack() }
        row("Map renderer",{ rendererValue() }) { chooseRenderer() }
        // MapLibre · 3D only.
        val orientation=row("Map orientation",{ if(p.getBoolean(Keys.OFFLINE_MAP_HEADING_UP,true)) "Heading-up 3D" else "North-up" }) {
            ThemedDialogs.Builder(a).setTitle("Map orientation while following GPS").setItems(arrayOf("Heading-up 3D","North-up")) { _,i->
                p.edit().putBoolean(Keys.OFFLINE_MAP_HEADING_UP,i==0).apply();refresh() }.show()
        }
        // MapLibre look: follow the launcher's light/dark theme, or always dark / light. Changing it reloads the map
        // (route, position and 3D buildings are put back by the map as on any reload).
        val appearance=row("Map appearance",{ MapAppearance.CHOICES[MapAppearance.index(a)] }) {
            ThemedDialogs.Builder(a).setTitle("Map appearance").setSingleChoiceItems(MapAppearance.CHOICES,MapAppearance.index(a)) { d,i->
                d.dismiss();MapAppearance.set(a,i);OfflineMapStore.touch(a);refresh()
            }.setNegativeButton("Cancel",null).show()
        }
        // A switch like the other on/off settings (default off); dimmed when the pack has no building heights.
        fun hasBuildings()=(pack as? NavigationPack.Status.Ready)?.pack?.vector?.buildings!=null
        val buildings=ui.toggle("3D buildings",p.getBoolean(Keys.OFFLINE_MAP_BUILDINGS,false),Runnable {
            if(!hasBuildings()) Toast.makeText(a,"This map pack has no building heights",Toast.LENGTH_SHORT).show()
            else {
                p.edit().putBoolean(Keys.OFFLINE_MAP_BUILDINGS,!p.getBoolean(Keys.OFFLINE_MAP_BUILDINGS,false)).apply()
                OfflineMapStore.touch(a);refresh()
            }
        })
        ui.add(group,buildings)
        refreshers.add {
            val available=hasBuildings()
            buildings.setChecked(available && p.getBoolean(Keys.OFFLINE_MAP_BUILDINGS,false),"3D buildings")
            buildings.alpha=if(available) 1f else .45f
        }
        // Mapsforge · 2D only: map files outside the navigation pack.
        val otherRegions=row("Other regions",{ regionValue() }) { regions() }
        refreshers.add { val vector=vectorInUse();visible(orientation,vector);visible(appearance,vector);visible(buildings,vector);visible(otherRegions,!vector) }
        // Shared by both renderers.
        toggle("Follow GPS position",Keys.OFFLINE_MAP_FOLLOW)
        row("Map center",{ if(p.getString(Keys.OFFLINE_MAP_LAT,"").isNullOrEmpty()) "Automatic" else "Custom" }) {
            ThemedDialogs.Builder(a).setTitle("Map center when there is no GPS fix").setItems(arrayOf("Automatic","Current GPS position","Enter coordinates")) { _,i->
                when(i) { 0->{ p.edit().remove(Keys.OFFLINE_MAP_LAT).remove(Keys.OFFLINE_MAP_LON).apply();refresh() };1->useLocation();else->editCenter() }
            }.show()
        }
        // 500 m and 1 km use "offline_map_span_m" and also store 2 km in "offline_map_span", so an older build
        // swapped in over this one shows the closest span it knows; km choices clear the metres key.
        row("Map view span",{ SettingsValues.mapSpan(p) }) {
            val spans=SettingsValues.SPANS
            ThemedDialogs.Builder(a).setTitle("Visible map area").setItems(spans.map { it.first }.toTypedArray()) { _,i->
                val (_,km,metres)=spans[i]
                val e=p.edit().putInt(Keys.OFFLINE_MAP_SPAN,km)
                if(metres>0) e.putInt(Keys.OFFLINE_MAP_SPAN_METRES,metres) else e.remove(Keys.OFFLINE_MAP_SPAN_METRES)
                e.apply();refresh()
            }.show()
        }
        trafficSettings(row = { name, value, action -> row(name, value, action) }, visible = ::visible)
    }

    /**
     * Peak hours ([TrafficEta]): on by default. Arrival estimates always use the road speeds; this adds the peak-hour
     * multiplier in the morning and evening windows. Not live traffic; routes are not changed.
     */
    private fun trafficSettings(row:(String,()->String,()->Unit)->android.view.View, visible:(android.view.View,Boolean)->Unit) {
        fun s()=TrafficEta.read(p)
        val traffic=ui.toggle("Peak hours in arrival times",s().enabled,Runnable {
            p.edit().putBoolean(Keys.OFFLINE_ETA_TRAFFIC,!s().enabled).apply();refresh()
        })
        ui.add(group,traffic)
        val advanced=row("Peak hours and speeds",{ trafficSummary(s()) }) { trafficPanel() }
        refreshers.add {
            val st=s()
            traffic.setChecked(st.enabled,"Peak hours in arrival times")
            advanced.alpha=if(st.enabled) 1f else .6f
        }
    }

    /** "07:30–09:30, 16:30–19:30 · Mon–Fri" (the peak schedule in one line). */
    private fun trafficSummary(s:TrafficEta.Settings)="${s.morning.label}, ${s.evening.label} · ${dayLabel(s.days,short=true)}"

    /** How estimates are made, and the peak windows and days. */
    private fun trafficPanel() {
        fun s()=TrafficEta.read(p)
        val box=ui.group()
        val scroll=android.widget.ScrollView(a).apply { addView(box) }
        lateinit var fill:()->Unit
        fun row(name:String,value:String,action:()->Unit) { ui.add(box,ui.valueRow(name,value,Runnable(action))) }
        fun changed() { fill();refresh() }
        fill={
            box.removeAllViews()
            val st=s()
            ui.add(box,ui.text("Arrival times assume %d km/h on Canal Road, %d km/h on Ring Road and motorways, and %d km/h on other roads. ".format(
                    TrafficEta.CANAL_KMH.roundToInt(),TrafficEta.FAST_KMH.roundToInt(),TrafficEta.CITY_KMH.roundToInt()) +
                "In peak hours they are multiplied by %.1f for trips over %d km and by %.1f for shorter trips. ".format(java.util.Locale.US,
                    TrafficEta.PEAK_LONG,(TrafficEta.PEAK_LONG_FROM_M/1000).roundToInt(),TrafficEta.PEAK_SHORT) +
                "Approximate, not live traffic; routes are not changed." + (if(st.enabled) "" else " Peak hours are off."),
                13,ui.t.textSecondary).apply { setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10)) })
            row("Morning peak",st.morning.label) { editWindow("Morning peak",Keys.OFFLINE_ETA_MORNING,s().morning) { changed() } }
            row("Evening peak",st.evening.label) { editWindow("Evening peak",Keys.OFFLINE_ETA_EVENING,s().evening) { changed() } }
            row("Days",dayLabel(st.days)) { chooseDays { changed() } }
        }
        fill()
        ThemedDialogs.Builder(a).setTitle("Peak hours and speeds").setView(scroll).setPositiveButton("Done",null).show()
    }

    private fun editWindow(title:String,key:String,current:TrafficEta.Window,done:()->Unit={}) {
        val input=EditText(a).apply { setSingleLine();hint="07:30-09:30";setText(current.stored);setTextColor(ui.t.textPrimary) }
        ThemedDialogs.Builder(a).setTitle(title).setMessage("Start and end time, 24-hour (for example 07:30-09:30).").setView(input)
            .setPositiveButton("Save") { _,_->
                val w=TrafficEta.Window.parse(input.text.toString())
                if(w==null) Toast.makeText(a,"Enter a time range such as 07:30-09:30",Toast.LENGTH_SHORT).show()
                else { p.edit().putString(key,w.stored).apply();refresh();done() }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun chooseDays(done:()->Unit={}) {
        val names=arrayOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
        val checked=BooleanArray(7) { TrafficEta.read(p).days and (1 shl it)!=0 }
        ThemedDialogs.Builder(a).setTitle("Days with these delays").setMultiChoiceItems(names,checked) { _,i,on-> checked[i]=on }
            .setPositiveButton("Save") { _,_->
                p.edit().putInt(Keys.OFFLINE_ETA_DAYS,(0 until 7).filter { checked[it] }.fold(0) { m,i-> m or (1 shl i) }).apply();refresh();done()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun dayLabel(days:Int,short:Boolean=false):String = when(days) {
        TrafficEta.WEEKDAYS -> if(short) "Mon–Fri" else "Monday–Friday"
        0b1111111 -> "Every day"
        0 -> "None"
        else -> listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").filterIndexed { i,_-> days and (1 shl i)!=0 }.joinToString(", ")
    }
    /** The pack's name as built (e.g. "Lahore"); ids and revisions stay internal. */
    private fun packLabel(pack:NavigationPack.Pack):String = pack.region
    private fun packValue():String = when(val s=pack) {
        null -> "Checking…"
        is NavigationPack.Status.Ready -> packLabel(s.pack)
        NavigationPack.Status.Missing -> "Not installed"
        is NavigationPack.Status.Invalid -> "Unusable"
    }
    /** Installed packs to choose from (unusable ones are listed with their problem), plus install help. */
    private fun choosePack() {
        val folder=NavigationPack.root(a)?.absolutePath ?: "Android/data/${a.packageName}/files/navigation"
        val help="Driving routes, place search and spoken directions need a region pack built by flare-lahore-tools. " +
            "Copy its folder (for example \"lahore\" or \"pakistan\", containing build-info.json) to:\n$folder"
        val list=packs
        if(list.isEmpty()) {
            ThemedDialogs.Builder(a).setTitle("Offline navigation pack").setMessage((pack?.message ?: "Checking…")+"\n\n"+help)
                .setPositiveButton("Close",null).show()
            return
        }
        val current=(pack as? NavigationPack.Status.Ready)?.pack?.dir?.name ?: NavigationPack.selected(a)
        val labels=list.map { (dir,status)->
            when(status) {
                // The folder is shown only to tell apart two packs with the same name.
                is NavigationPack.Status.Ready -> packLabel(status.pack) +
                    (if(list.count { (it.second as? NavigationPack.Status.Ready)?.pack?.region==status.pack.region }>1) "  (${dir.name})" else "")
                is NavigationPack.Status.Invalid -> "${dir.name} · unusable: ${status.reason}"
                NavigationPack.Status.Missing -> dir.name
            }
        }.toTypedArray()
        ThemedDialogs.Builder(a).setTitle("Offline navigation pack")
            .setSingleChoiceItems(labels,list.indexOfFirst { it.first.name==current }) { dialog,i->
                val (dir,status)=list[i]
                if(status !is NavigationPack.Status.Ready) {
                    Toast.makeText(a,"${dir.name} is unusable; reinstall it or choose another pack",Toast.LENGTH_LONG).show()
                    return@setSingleChoiceItems
                }
                dialog.dismiss()
                if(dir.name==current) return@setSingleChoiceItems
                NavigationPack.select(a,dir.name)
                pack=null;refresh();checkPack()
                Toast.makeText(a,"${status.pack.region} selected for maps, search and routing",Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("How to install") { _,_->
                ThemedDialogs.Builder(a).setTitle("Install a navigation pack").setMessage(help).setPositiveButton("Close",null).show()
            }
            .setNegativeButton("Close",null).show()
    }
    /** Pack has a 3D map: null while checking, false without a usable pack. */
    private fun packVector():Boolean? = when(val s=pack) { null -> null;is NavigationPack.Status.Ready -> s.pack.vector!=null;else -> false }
    private fun vectorInUse()=SettingsValues.vectorInUse(p.getBoolean(Keys.OFFLINE_MAP_VECTOR,true),rendererProblem,packVector())
    private fun rendererValue()=SettingsValues.mapRenderer(p.getBoolean(Keys.OFFLINE_MAP_VECTOR,true),rendererProblem,packVector())
    private fun chooseRenderer() {
        val options=arrayOf("MapLibre · 3D","Mapsforge · 2D")
        val chosen=if(p.getBoolean(Keys.OFFLINE_MAP_VECTOR,true) && rendererProblem==null) 0 else 1
        ThemedDialogs.Builder(a).setTitle("Map renderer").setSingleChoiceItems(options,chosen) { dialog,i->
            dialog.dismiss()
            if(i==0 && rendererProblem!=null) {
                ThemedDialogs.Builder(a).setTitle("MapLibre · 3D unavailable")
                    .setMessage("The tilted 3D map $rendererProblem on this unit. The standard Mapsforge · 2D map is used instead.")
                    .setPositiveButton("Close",null).show()
                return@setSingleChoiceItems
            }
            p.edit().putBoolean(Keys.OFFLINE_MAP_VECTOR,i==0).apply();refresh()
        }.setNegativeButton("Cancel",null).show()
    }
    private fun regionValue():String {
        if(OfflineMapStore.busy) return OfflineMapStore.status.ifBlank { "Downloading…" }
        val f=OfflineMapStore.region(a)
        return if(f.isFile) "%.1f MB region".format(java.util.Locale.US,f.length()/1048576.0) else "None"
    }
    /** Mapsforge regions outside the navigation pack: catalog, URL, download, import, remove (or cancel a transfer). */
    private fun regions() {
        if(OfflineMapStore.busy) {
            ThemedDialogs.Builder(a).setTitle("Region download").setMessage(OfflineMapStore.status.ifBlank { "Downloading…" })
                .setPositiveButton("Cancel download") { _,_->OfflineMapStore.cancel();refresh() }.setNegativeButton("Close",null).show()
            return
        }
        val url=p.getString(Keys.OFFLINE_MAP_URL,"").orEmpty()
        val items=arrayListOf("Browse region catalog","Enter region URL")
        if(url.isNotBlank()) items.add("Download "+url.substringAfterLast('/'))
        items.add("Import .map file")
        if(OfflineMapStore.region(a).isFile) items.add("Remove downloaded region")
        ThemedDialogs.Builder(a).setTitle("Other regions (Mapsforge)").setItems(items.toTypedArray()) { _,i->
            when(items[i]) {
                "Browse region catalog" -> MapRegions.show(a) { chosen->p.edit().putString(Keys.OFFLINE_MAP_URL,chosen).apply();refresh() }
                "Enter region URL" -> editUrl()
                "Import .map file" -> try { a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),PICK_MAP) }
                    catch(_:android.content.ActivityNotFoundException) { Toast.makeText(a,"No file picker is available",Toast.LENGTH_SHORT).show() }
                "Remove downloaded region" -> ThemedDialogs.Builder(a).setTitle("Remove downloaded region?").setMessage("Map files are shared by all driver profiles. The bundled world overview stays available.")
                    .setPositiveButton("Remove") { _,_->OfflineMapStore.delete(a);refresh() }.setNegativeButton("Cancel",null).show()
                else -> OfflineMapStore.download(a,url)
            }
        }.show()
    }
    private fun editUrl() {
        val input=EditText(a).apply { setSingleLine();hint="https://…/region.map";setText(p.getString(Keys.OFFLINE_MAP_URL,""));setTextColor(ui.t.textPrimary) }
        ThemedDialogs.Builder(a).setTitle("Offline region URL").setMessage("A direct HTTPS Mapsforge .map file, up to 512 MB.")
            .setView(input).setPositiveButton("Save") { _,_->p.edit().putString(Keys.OFFLINE_MAP_URL,input.text.toString().trim()).apply();refresh() }.setNegativeButton("Cancel",null).show()
    }
    private fun editCenter() {
        val box=ui.column();val lat=EditText(a).apply { hint="Latitude";setSingleLine();setText(p.getString(Keys.OFFLINE_MAP_LAT,""));setTextColor(ui.t.textPrimary) }
        val lon=EditText(a).apply { hint="Longitude";setSingleLine();setText(p.getString(Keys.OFFLINE_MAP_LON,""));setTextColor(ui.t.textPrimary) };box.addView(lat);box.addView(lon)
        val dialog=ThemedDialogs.Builder(a).setTitle("Custom map center").setView(box).setPositiveButton("Apply",null).setNeutralButton("Automatic") { _,_->p.edit().remove(Keys.OFFLINE_MAP_LAT).remove(Keys.OFFLINE_MAP_LON).apply();refresh() }.setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val x=lat.text.toString().trim().toDoubleOrNull();val y=lon.text.toString().trim().toDoubleOrNull()
            if(x==null || y==null || !x.isFinite() || !y.isFinite() || x !in -85.0..85.0 || y !in -180.0..180.0) lat.error="Enter valid latitude and longitude"
            else { p.edit().putString(Keys.OFFLINE_MAP_LAT,x.toString()).putString(Keys.OFFLINE_MAP_LON,y.toString()).apply();refresh();dialog.dismiss() }
        } };dialog.show()
    }
    private fun useLocation() {
        if(a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) { a.requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),REQUEST_LOCATION);return }
        val manager=a.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
        val location=try { listOf(LocationManager.GPS_PROVIDER,LocationManager.PASSIVE_PROVIDER).mapNotNull { manager?.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch(_:RuntimeException) { null }
        if(location==null) Toast.makeText(a,"No GPS fix yet. Try again outdoors.",Toast.LENGTH_LONG).show()
        else { p.edit().putString(Keys.OFFLINE_MAP_LAT,location.latitude.toString()).putString(Keys.OFFLINE_MAP_LON,location.longitude.toString()).apply();refresh() }
    }
    private fun refresh() { refreshers.forEach { it() } }
    fun resume() {
        OfflineMapStore.add(this);PackImport.add(this);refresh();checkPack()
    }
    private fun importPack() {
        if(PackImport.busy) {
            ThemedDialogs.Builder(a).setTitle("Importing navigation pack").setMessage(PackImport.status)
                .setPositiveButton("Cancel import") { _,_->PackImport.cancel() }.setNegativeButton("Close",null).show()
            return
        }
        ThemedDialogs.Builder(a).setTitle("Import navigation pack")
            .setMessage("Choose a navigation pack ZIP (for example from Downloads or a USB drive). It is extracted and checked " +
                "here; a pack with the same region replaces the installed one only after every file is verified.")
            .setPositiveButton("Choose ZIP") { _,_->
                try {
                    a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/zip","application/x-zip-compressed","application/octet-stream")),PICK_PACK)
                } catch(_:android.content.ActivityNotFoundException) { Toast.makeText(a,"No file picker is available",Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel",null).show()
    }
    /** Result of [PackImport] (on the main thread): reload the pack list and selection, and say what happened. */
    fun packImportDone(ok:Boolean,message:String) {
        pack=null;refresh();checkPack()
        if(!a.isDestroyed) ThemedDialogs.Builder(a).setTitle(if(ok) "Navigation pack imported" else "Import failed")
            .setMessage(message.removePrefix("Import failed: ")).setPositiveButton("Close",null).show()
    }
    /** Lists installed packs, then verifies the selected one (checksums the first time: slow for a large pack). */
    private fun checkPack() {
        OfflineRouting.worker.execute {
            PackImport.recover(a) // leftovers of an import interrupted by a restart
            val installed=NavigationPack.installed(a).map { it to NavigationPack.read(it) }
            a.runOnUiThread { if(!a.isDestroyed) { packs=installed;refresh() } }
            val status=NavigationPack.verified(a)
            a.runOnUiThread { if(!a.isDestroyed) { pack=status;refresh() } }
        }
    }
    fun pause() { OfflineMapStore.remove(this);PackImport.remove(this) }
    override fun mapStatusChanged()=refresh()
    companion object { const val PICK_MAP=734; const val PICK_PACK=735; private const val REQUEST_LOCATION=733 }
}
