package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal class UpdateChecker(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    @Volatile private var disposed = false
    @Volatile private var activeConnection: HttpURLConnection? = null
    private var worker: Thread? = null
    private var awaitingPermission = false
    private val update = File(activity.cacheDir, "verified-update.apk")
    private val releasePage = "https://github.com/Afshaal/flare-auto/releases/latest"

    fun check() {
        if (busy || disposed) return
        busy = true
        toast("Checking GitHub for updates…")
        runWork {
            try {
                val connection = URL("https://api.github.com/repos/Afshaal/flare-auto/releases/latest").openConnection() as HttpURLConnection
                activeConnection = connection
                val raw = try {
                    if (disposed) throw java.io.InterruptedIOException()
                    connection.connectTimeout = 10000; connection.readTimeout = 10000
                    connection.setRequestProperty("User-Agent", "Flare-Auto-Update-Checker")
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    if (connection.responseCode != 200) throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(8192); val out = StringBuilder()
                        while (out.length <= 200000) { val n = reader.read(buffer, 0, minOf(buffer.size, 200001 - out.length)); if (n <= 0) break; out.append(buffer, 0, n) }
                        if (out.length > 200000) throw IllegalStateException("Release information is too large")
                        out.toString()
                    }
                } finally { connection.disconnect() }
                val json = JSONObject(raw)
                val tag = json.getString("tag_name")
                val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
                val order = UpdateVersion.compare(tag, current)
                val assets = json.optJSONArray("assets")
                val urls = ArrayList<String>()
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val parsed = Uri.parse(url)
                    if (name.endsWith(".apk", true) && parsed.scheme == "https" && parsed.host.equals("github.com", true) &&
                        parsed.path?.startsWith("/Afshaal/flare-auto/releases/download/") == true) urls.add(url)
                }
                val notes = json.optString("body").trim().take(2500)
                main.post {
                    busy = false
                    if (disposed || activity.isFinishing || activity.isDestroyed) return@post
                    if (order != null && order <= 0) {
                        dialog("No newer release", "Installed: $current\nLatest stable release: $tag")
                    } else {
                        val message = "Installed: $current\nLatest stable release: $tag" +
                            (if (order == null) "\nVersion comparison unavailable; check the release before installing." else "") +
                            (if (notes.isNotEmpty()) "\n\n$notes" else "")
                        val builder = ThemedDialogs.Builder(activity).setTitle(if (order == null) "Latest release" else "Update available").setMessage(message)
                        if (urls.size == 1 && order != null) builder.setPositiveButton("Download") { _, _ -> download(urls[0]) }
                        else builder.setPositiveButton("Open release") { _, _ -> openRelease() }
                        builder.setNegativeButton("Cancel", null).show()
                    }
                }
            } catch (e: Exception) { fail(e) }
        }
    }

    private fun download(address: String) {
        if (busy || disposed) return
        busy = true
        toast("Downloading update…")
        runWork {
            var temporary: File? = null
            try {
                val temp = File.createTempFile("flare-update-", ".tmp", activity.cacheDir)
                temporary = temp
                val conn = URL(address).openConnection() as HttpURLConnection
                activeConnection = conn
                try {
                    if (disposed) throw java.io.InterruptedIOException()
                    conn.connectTimeout = 15000; conn.readTimeout = 30000; conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Flare-Auto-Update-Checker")
                    if (conn.responseCode != 200 || conn.url.protocol != "https") throw IllegalStateException("Download failed")
                    conn.inputStream.use { input -> temp.outputStream().use { output ->
                        val buffer = ByteArray(16384); var size = 0L
                        while (true) {
                            if (disposed || Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                            val n = input.read(buffer); if (n < 0) break; size += n
                            if (size > 100L * 1024 * 1024) throw IllegalStateException("Update is too large")
                            output.write(buffer, 0, n)
                        }
                    } }
                } finally { conn.disconnect() }
                if (disposed) throw java.io.InterruptedIOException()
                verify(temp)
                if (!temp.renameTo(update)) { temp.copyTo(update, overwrite = true); temp.delete() }
                main.post { busy = false; if (!disposed && !activity.isFinishing && !activity.isDestroyed) requestInstall() }
            } catch (e: Exception) { fail(e) }
            finally { temporary?.delete() }
        }
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File) {
        val pm = activity.packageManager
        val flags = PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        val installed = pm.getPackageInfo(activity.packageName, flags)
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IllegalStateException("Downloaded file is not a valid APK")
        if (candidate.packageName != activity.packageName) throw IllegalStateException("APK package does not match this app")
        val candidateCode = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        if (candidateCode <= installedCode) throw IllegalStateException("APK is not newer than this installed build")
        fun signatures(info: PackageInfo): Set<String> {
            val signed = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners ?: info.signatures else info.signatures
            return signed?.map { it.toCharsString() }?.toSet() ?: emptySet()
        }
        val current = signatures(installed)
        if (current.isEmpty() || current != signatures(candidate)) throw IllegalStateException("APK signing key does not match this installation")
    }

    private fun requestInstall() {
        if (disposed || !update.isFile) return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            ThemedDialogs.Builder(activity).setTitle("Allow update installation")
                .setMessage("Android needs permission to install the verified update. Return here after enabling it.")
                .setPositiveButton("Open settings") { _, _ ->
                    try { awaitingPermission = true; activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                    catch (_: Exception) { awaitingPermission = false; dialog("Cannot open settings", "Allow installs from Flare Auto in Android settings, then try again.") }
                }.setNegativeButton("Cancel", null).show()
            return
        }
        try {
            val uri = Uri.parse("content://${activity.packageName}.updates/update.apk")
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { dialog("Cannot open installer", "The Android package installer is unavailable.") }
    }

    fun onResume() {
        if (awaitingPermission) {
            awaitingPermission = false
            if (Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()) requestInstall()
        }
    }
    private fun runWork(block: () -> Unit) {
        worker = Thread {
            try { if (!disposed) block() }
            finally { activeConnection = null }
        }.also { it.start() }
    }

    fun dispose() {
        disposed = true
        activeConnection?.disconnect()
        worker?.interrupt()
        main.removeCallbacksAndMessages(null)
    }

    private fun openRelease() {
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasePage))) }
        catch (_: Exception) { dialog("Browser unavailable", releasePage) }
    }
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    private fun dialog(title: String, message: String) { ThemedDialogs.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK", null).show() }
    private fun fail(error: Exception) { main.post { busy = false; if (!disposed && !activity.isFinishing && !activity.isDestroyed) dialog("Update check failed", error.message ?: "Check your internet connection and try again.") } }
}
