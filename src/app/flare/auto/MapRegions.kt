package app.flare.auto

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URI

internal object MapRegions {
    private const val ROOT="https://download.mapsforge.org/maps/v5/"
    fun show(a:Activity,folder:String=ROOT,selected:(String)->Unit) {
        if(OfflineMapStore.busy) { android.widget.Toast.makeText(a,"Finish or cancel the current map transfer first",android.widget.Toast.LENGTH_SHORT).show();return }
        var wanted=true
        val loading=ThemedDialogs.Builder(a).setTitle("Offline map regions").setMessage("Loading region list…").setNegativeButton("Cancel") { _,_->wanted=false }.setOnCancelListener { wanted=false }.show()
        val weak=WeakReference(a)
        OfflineMapStore.worker.execute {
            val result=runCatching {
                val connection=URI(folder).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout=15000;connection.readTimeout=15000;connection.setRequestProperty("User-Agent","FlareAuto/${appVersion(a)} map-region-browser")
                try {
                    val html=connection.inputStream.use { String(it.readBytes().also { b->check(b.size<512000) { "Region list too large" } },Charsets.UTF_8) }
                    Regex("href=\"([^\"]+)\"",RegexOption.IGNORE_CASE).findAll(html).mapNotNull { match->
                        val href=match.groupValues[1]
                        if(href=="../" || href.startsWith('?') || !(href.endsWith('/') || href.endsWith(".map"))) null
                        else URI(folder).resolve(href).takeIf { it.scheme=="https" && it.host=="download.mapsforge.org" && it.path.startsWith("/maps/v5/") && it.toString()!=folder }?.toString()
                    }.distinct().sorted().toList()
                } finally { connection.disconnect() }
            }
            Handler(Looper.getMainLooper()).post {
                val owner=weak.get()
                if(!wanted || owner==null || owner.isDestroyed || owner.isFinishing) return@post
                loading.dismiss()
                result.fold({ entries->
                    val choices=if(folder==ROOT) entries else listOf(URI(folder).resolve("../").toString())+entries
                    val names=choices.mapIndexed { n,url->
                        if(folder!=ROOT && n==0) "‹ Parent region" else java.net.URLDecoder.decode(url.trimEnd('/').substringAfterLast('/'),"UTF-8").removeSuffix(".map").replace('-',' ').replaceFirstChar { it.uppercase() }+if(url.endsWith('/')) "  ›" else ""
                    }.toTypedArray()
                    ThemedDialogs.Builder(owner).setTitle("Choose offline coverage").setItems(names) { _,i->
                        val url=choices[i]
                        if(url.endsWith('/')) show(owner,url,selected) else selected(url)
                    }.setNegativeButton("Cancel",null).show()
                },{ error->ThemedDialogs.Builder(owner).setTitle("Region list unavailable").setMessage(error.message ?: "Check the internet connection, or import a map from USB.").setPositiveButton("Close",null).show() })
            }
        }
    }
}
