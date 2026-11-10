package app.flare.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class UpdateFileProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = if (uri.path == "/update.apk") "application/vnd.android.package-archive" else null
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.path != "/update.apk" || mode != "r") throw FileNotFoundException()
        return ParcelFileDescriptor.open(File(context!!.cacheDir, "verified-update.apk"), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.path != "/update.apk") return null
        val columns = projection ?: arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE)
        val cursor = android.database.MatrixCursor(columns)
        val file = File(context!!.cacheDir, "verified-update.apk")
        cursor.addRow(columns.map { when (it) {
            android.provider.OpenableColumns.DISPLAY_NAME -> "FlareAuto-update.apk"
            android.provider.OpenableColumns.SIZE -> file.length()
            else -> null
        } })
        return cursor
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
