package app.flare.auto

import android.app.Activity
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import java.io.File

internal object OfflineDestinationPicker {
    data class Place(val name:String,val kind:String,val lat:Double,val lon:Double)
    fun show(a:Activity,chosen:(Place)->Unit):android.app.Dialog {
        val ui=UiKit(a);val main=Handler(Looper.getMainLooper());var token=0;var open=true
        val body=ui.column();val input=EditText(a).apply { setSingleLine();hint="Place, street or latitude, longitude";setTextColor(ui.t.textPrimary) }
        body.addView(input)
        val status=ui.text("Search Lahore offline",12,ui.t.textSecondary);body.addView(status)
        val list=ListView(a).apply { divider=null };body.addView(list,LinearLayout.LayoutParams(MATCH,minOf(ui.dp(190),(a.resources.displayMetrics.heightPixels*.32f).toInt())))
        var places=emptyList<Place>()
        val dialog=ThemedDialogs.Builder(a).setTitle("Offline destination").setView(body).setNegativeButton("Close",null).create()
        list.setOnItemClickListener { _,_,i,_->places.getOrNull(i)?.let { dialog.dismiss();chosen(it) } }
        val search=Runnable {
            val query=input.text.toString().trim();val request=++token
            if(query.length<2) { status.text="Enter at least two characters";list.adapter=null;return@Runnable }
            status.text="Searching local places…"
            OfflineRouting.worker.execute {
                val found=runCatching {
                    val coordinates=query.split(',').map { it.trim().toDoubleOrNull() }
                    if(coordinates.size==2 && coordinates.all { it!=null } && NavigationPack.covers(coordinates[0]!!,coordinates[1]!!)) listOf(Place("Selected coordinates","coordinate",coordinates[0]!!,coordinates[1]!!))
                    else {
                        val file=File(NavigationPack.ensure(a.applicationContext),"search.sqlite")
                        val terms=query.split(Regex("\\s+")).filter { it.any(Char::isLetterOrDigit) }.take(6).joinToString(" ") { "\""+it.replace("\"","\"\"")+"\"*" }
                        if(terms.isBlank()) emptyList() else SQLiteDatabase.openDatabase(file.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use { db->
                            db.rawQuery("SELECT p.name,p.kind,p.lat,p.lon FROM places p JOIN places_fts f ON p.id=f.rowid WHERE places_fts MATCH ? LIMIT 40",arrayOf(terms)).use { rows->
                                buildList { while(rows.moveToNext()) add(Place(rows.getString(0),rows.getString(1),rows.getDouble(2),rows.getDouble(3))) }
                            }
                        }
                    }
                }
                main.post {
                    if(!open || request!=token || a.isDestroyed) return@post
                    places=found.getOrDefault(emptyList())
                    status.text=if(found.isFailure) "Search unavailable: ${found.exceptionOrNull()?.message}" else if(places.isEmpty()) "No local match. Try coordinates or long-press the map." else "${places.size} local matches"
                    list.adapter=object:BaseAdapter() {
                        override fun getCount()=places.size
                        override fun getItem(i:Int)=places[i]
                        override fun getItemId(i:Int)=i.toLong()
                        override fun getView(i:Int,v:android.view.View?,parent:android.view.ViewGroup)=ui.text(places[i].name+"\n"+places[i].kind,14,ui.t.textPrimary).apply { minHeight=ui.dp(54);setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6)) }
                    }
                }
            }
        }
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { token++;main.removeCallbacks(search);main.postDelayed(search,300) }
            override fun afterTextChanged(e:Editable?)=Unit
        })
        dialog.setOnDismissListener { open=false;token++;main.removeCallbacks(search) };dialog.show();return dialog
    }
}
