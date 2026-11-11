package app.flare.auto

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

internal object NavigationPack {
    const val VERSION="lahore-20261109-v1"
    fun covers(lat:Double,lon:Double)=lat.isFinite() && lon.isFinite() && lat in 31.05..31.85 && lon in 73.90..74.80
    @Synchronized fun ensure(c:Context):File {
        val folder=File(c.filesDir,VERSION)
        if(File(folder,"ready").isFile) return folder
        folder.mkdirs();check(folder.usableSpace>160L*1024*1024) { "At least 160 MB free storage is required for Lahore navigation" }
        val base=folder.canonicalPath+File.separator
        ZipInputStream(c.assets.open("maps/lahore-navigation.zip")).use { zip->
            var total=0L
            while(true) {
                val entry=zip.nextEntry ?: break
                val target=File(folder,entry.name)
                check(target.canonicalPath.startsWith(base)) { "Invalid navigation package path" }
                if(entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out->
                        val buffer=ByteArray(65536)
                        while(true) { val n=zip.read(buffer);if(n<0) break;total+=n;check(total<256L*1024*1024) { "Navigation package is too large" };out.write(buffer,0,n) }
                    }
                }
            }
        }
        check(File(folder,"gh/properties").isFile && File(folder,"search.sqlite").isFile) { "Incomplete navigation package" }
        File(folder,"ready").writeText(VERSION);return folder
    }
}
