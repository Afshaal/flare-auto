package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.location.LocationManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

internal class OfflineMapPanel(private val a:Activity,private val ui:UiKit,group:LinearLayout):OfflineMapStore.Listener {
    private val p=ProfileStore.preferences(a)
    private val refreshers=ArrayList<()->Unit>()
    /** Navigation pack state, checked on the routing thread whenever settings resume. */
    private var pack:NavigationPack.Status?=null
    /**
     * Offline maps settings, kept to the controls a driver uses: on/off, the navigation pack, the 3D map and its
     * orientation, following, the default map view, and one "Other regions" row that gathers the Mapsforge region
     * actions (catalog, URL, download, import, remove) behind a single list. Attribution lives on the map ("i"
     * button) and in About › Open-source credits.
     */
    init {
        val enabled=ui.toggle("Enable offline maps",p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false),Runnable {
            p.edit().putBoolean(Keys.OFFLINE_MAP_ENABLED,!p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false)).apply();refresh()
        })
        ui.add(group,enabled);refreshers.add { enabled.switch.isChecked=p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false) }
        fun row(name:String,value:()->String,action:()->Unit) {
            val v=ui.valueRow(name,value(),Runnable(action));ui.add(group,v);refreshers.add { v.setValue(value()) }
        }
        fun toggle(name:String,key:String) {
            val t=ui.toggle(name,p.getBoolean(key,true),Runnable { p.edit().putBoolean(key,!p.getBoolean(key,true)).apply();refresh() })
            ui.add(group,t);refreshers.add { t.switch.isChecked=p.getBoolean(key,true) }
        }
        row("Navigation pack",{ packValue() }) {
            val folder=NavigationPack.root(a)?.absolutePath ?: "Android/data/${a.packageName}/files/navigation"
            ThemedDialogs.Builder(a).setTitle("Offline navigation pack").setMessage((pack?.message ?: "Checking…") +
                "\n\nDriving routes, place search and spoken directions need the region pack built by flare-lahore-tools. " +
                "Copy its folder (for example \"lahore\", containing build-info.json) to:\n$folder")
                .setPositiveButton("Close",null).show()
        }
        toggle("3D vector map",Keys.OFFLINE_MAP_VECTOR)
        row("Map orientation",{ if(p.getBoolean(Keys.OFFLINE_MAP_HEADING_UP,true)) "Heading-up 3D" else "North-up" }) {
            ThemedDialogs.Builder(a).setTitle("Map orientation while following GPS").setItems(arrayOf("Heading-up 3D","North-up")) { _,i->
                p.edit().putBoolean(Keys.OFFLINE_MAP_HEADING_UP,i==0).apply();refresh() }.show()
        }
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
        row("Other regions",{ regionValue() }) { regions() }
    }
    private fun packValue():String = when(val s=pack) {
        null -> "Checking…"
        is NavigationPack.Status.Ready -> s.pack.id.substringAfterLast('-').removePrefix("r").toIntOrNull()
            ?.let { "${s.pack.region} · revision $it" } ?: s.pack.region
        NavigationPack.Status.Missing -> "Not installed"
        is NavigationPack.Status.Invalid -> "Unusable"
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
        OfflineMapStore.add(this);refresh()
        OfflineRouting.worker.execute {
            val status=NavigationPack.verified(a)
            a.runOnUiThread { if(!a.isDestroyed) { pack=status;refresh() } }
        }
    }
    fun pause() { OfflineMapStore.remove(this) }
    override fun mapStatusChanged()=refresh()
    companion object { const val PICK_MAP=734; private const val REQUEST_LOCATION=733 }
}
