package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.location.LocationManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

internal class OfflineMapPanel(private val a:Activity,private val ui:UiKit,group:LinearLayout):OfflineMapStore.Listener {
    private val p=ProfileStore.preferences(a)
    private val refreshers=ArrayList<()->Unit>()
    init {
        val enabled=ui.toggle("Enable offline maps",p.getBoolean("offline_map_enabled",false),Runnable {
            p.edit().putBoolean("offline_map_enabled",!p.getBoolean("offline_map_enabled",false)).apply();refresh()
        })
        ui.add(group,enabled);refreshers.add { (enabled.getChildAt(1) as android.widget.Switch).isChecked=p.getBoolean("offline_map_enabled",false) }
        fun row(name:String,value:()->String,action:()->Unit) {
            val v=ui.valueRow(name,value(),Runnable(action));ui.add(group,v);refreshers.add { (v.getChildAt(1) as TextView).text=value() }
        }
        row("Map center",{ if(p.getString("offline_map_lat","").isNullOrEmpty()) "Automatic / map center" else p.getString("offline_map_lat","")+", "+p.getString("offline_map_lon","") }) { editCenter() }
        row("Use current GPS position",{ "Requires location access" }) { useLocation() }
        row("Map view span",{ "${p.getInt("offline_map_span",10)} km across" }) {
            val values=intArrayOf(2,5,10,20,50,100,200)
            ThemedDialogs.Builder(a).setTitle("Visible map area").setItems(values.map { "$it km across" }.toTypedArray()) { _,i->p.edit().putInt("offline_map_span",values[i]).apply();refresh() }.show()
        }
        val follow=ui.toggle("Follow GPS position",p.getBoolean("offline_map_follow",true),Runnable { p.edit().putBoolean("offline_map_follow",!p.getBoolean("offline_map_follow",true)).apply();refresh() })
        ui.add(group,follow);refreshers.add { (follow.getChildAt(1) as android.widget.Switch).isChecked=p.getBoolean("offline_map_follow",true) }
        row("Map card size",{ "Resize in Home editor" }) { a.startActivity(Intent(a,GridEditorActivity::class.java)) }
        row("Browse offline regions",{ "Mapsforge download catalog" }) { MapRegions.show(a) { url->p.edit().putString("offline_map_url",url).apply();refresh() } }
        row("Offline map source",{ p.getString("offline_map_url","")?.takeIf { it.isNotBlank() } ?: "Choose a .map download URL" }) {
            val input=EditText(a).apply { setSingleLine();hint="https://…/region.map";setText(p.getString("offline_map_url",""));setTextColor(ui.t.textPrimary) }
            ThemedDialogs.Builder(a).setTitle("Offline region URL").setMessage("Use a direct HTTPS Mapsforge .map file, up to 512 MB. Regional files contain their full published coverage; view span changes the displayed frame, not the downloaded file.")
                .setView(input).setPositiveButton("Save") { _,_->p.edit().putString("offline_map_url",input.text.toString().trim()).apply();refresh() }.setNegativeButton("Cancel",null).show()
        }
        row("Download selected region",{ if(OfflineMapStore.busy) "Downloading…" else "Manual download" }) { OfflineMapStore.download(a,p.getString("offline_map_url","").orEmpty()) }
        row("Import offline map",{ "Choose .map from storage or USB" }) {
            try { a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),PICK_MAP) }
            catch(_:android.content.ActivityNotFoundException) { Toast.makeText(a,"No file picker is available",Toast.LENGTH_SHORT).show() }
        }
        row("Map storage",{
            val f=OfflineMapStore.region(a)
            if(f.isFile) "%.1f MB · map file".format(java.util.Locale.US,f.length()/1048576.0) else "Bundled world overview · no street detail"
        }) { a.startActivity(Intent(a,OfflineMapActivity::class.java)) }
        row("Download status",{ OfflineMapStore.status.ifBlank { "No transfer running" } }) { if(OfflineMapStore.busy) OfflineMapStore.cancel() }
        row("Remove downloaded map",{ "Keep bundled world overview" }) {
            ThemedDialogs.Builder(a).setTitle("Remove downloaded region?").setMessage("Map files are shared by all driver profiles. The bundled world overview stays available.")
                .setPositiveButton("Remove") { _,_->OfflineMapStore.delete(a);refresh() }.setNegativeButton("Cancel",null).show()
        }
        row("Map credits",{ "© OpenStreetMap contributors · Mapsforge" }) {
            ThemedDialogs.Builder(a).setTitle("Offline maps").setMessage("OpenStreetMap data © OpenStreetMap contributors, ODbL 1.0. Mapsforge renderer, LGPL-3.0 with linking exception. Bundled world overview: mapsforge.org, 2016-02-20; outlines only. Offline street maps require a regional .map file. This version displays maps and GPS position; it does not calculate routes or spoken directions.")
                .setPositiveButton("Close",null).show()
        }
    }
    private fun editCenter() {
        val box=ui.column();val lat=EditText(a).apply { hint="Latitude";setSingleLine();setText(p.getString("offline_map_lat",""));setTextColor(ui.t.textPrimary) }
        val lon=EditText(a).apply { hint="Longitude";setSingleLine();setText(p.getString("offline_map_lon",""));setTextColor(ui.t.textPrimary) };box.addView(lat);box.addView(lon)
        val dialog=ThemedDialogs.Builder(a).setTitle("Custom map center").setView(box).setPositiveButton("Apply",null).setNeutralButton("Automatic") { _,_->p.edit().remove("offline_map_lat").remove("offline_map_lon").apply();refresh() }.setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val x=lat.text.toString().trim().toDoubleOrNull();val y=lon.text.toString().trim().toDoubleOrNull()
            if(x==null || y==null || !x.isFinite() || !y.isFinite() || x !in -85.0..85.0 || y !in -180.0..180.0) lat.error="Enter valid latitude and longitude"
            else { p.edit().putString("offline_map_lat",x.toString()).putString("offline_map_lon",y.toString()).apply();refresh();dialog.dismiss() }
        } };dialog.show()
    }
    private fun useLocation() {
        if(a.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED) { a.requestPermissions(arrayOf("android.permission.ACCESS_FINE_LOCATION"),733);return }
        val manager=a.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
        val location=try { listOf(LocationManager.GPS_PROVIDER,LocationManager.PASSIVE_PROVIDER).mapNotNull { manager?.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch(_:RuntimeException) { null }
        if(location==null) Toast.makeText(a,"No GPS fix yet. Try again outdoors.",Toast.LENGTH_LONG).show()
        else { p.edit().putString("offline_map_lat",location.latitude.toString()).putString("offline_map_lon",location.longitude.toString()).apply();refresh() }
    }
    private fun refresh() { refreshers.forEach { it() } }
    fun resume() { OfflineMapStore.add(this);refresh() }
    fun pause() { OfflineMapStore.remove(this) }
    override fun mapStatusChanged()=refresh()
    companion object { const val PICK_MAP=734 }
}
