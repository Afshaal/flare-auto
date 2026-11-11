package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LimitChecksActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (intent.getBooleanExtra("editor", false)) {
            startActivity(Intent(this, GridEditorActivity::class.java)); finish(); return
        }
        val results = ArrayList<String>()
        val output = TextView(this).apply { textSize = 14f }; setContentView(output)
        fun test(name: String, block: () -> Unit) {
            try { block(); results.add("PASS $name") } catch (e: Throwable) { results.add("FAIL $name: ${e.cause?.message ?: e.message}") }
        }
        var pressed = 0
        test("choice dialog positive negative neutral buttons") {
            val dialog = ThemedDialogs.Builder(this).setTitle("Fixture").setItems(arrayOf("One", "Two")) { _, _ -> }
                .setPositiveButton("Accept") { _, which -> pressed = which }
                .setNegativeButton("Cancel") { _, _ -> }.setNeutralButton("Other") { _, _ -> }.show()
            check(dialog.getButton(DialogInterface.BUTTON_POSITIVE) != null)
            check(dialog.getButton(DialogInterface.BUTTON_NEGATIVE) != null)
            check(dialog.getButton(DialogInterface.BUTTON_NEUTRAL) != null)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            dialog.dismiss()
        }
        val loadedOnWorker = AtomicBoolean(false)
        val deferred = AsyncImages.load(this) {
            loadedOnWorker.set(Looper.myLooper() != Looper.getMainLooper())
            ColorDrawable(Color.RED)
        }
        val candidate = File(getExternalFilesDir(null), "candidate.apk")
        val wrong = File(getExternalFilesDir(null), "wrong-signer.apk")
        val same = File(getExternalFilesDir(null), "same-version.apk")
        val updater = UpdateChecker(this)
        val verify = UpdateChecker::class.java.getDeclaredMethod("verify", File::class.java).apply { isAccessible = true }
        if (candidate.isFile) {
            test("newer signed update accepted") { verify.invoke(updater, candidate) }
            if (wrong.isFile) test("wrong signer rejected") {
                val error = runCatching { verify.invoke(updater, wrong) }.exceptionOrNull()
                check(error?.cause?.message?.contains("signing key") == true)
            }
            if (same.isFile) test("same version update rejected") {
                val error = runCatching { verify.invoke(updater, same) }.exceptionOrNull()
                check(error?.cause?.message?.contains("not newer") == true)
            }
            if (intent.getBooleanExtra("installer", false)) {
                verify.invoke(updater, candidate)
                candidate.copyTo(File(cacheDir, "verified-update.apk"), overwrite = true)
                UpdateChecker::class.java.getDeclaredMethod("requestInstall").apply { isAccessible = true }.invoke(updater)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            test("choice button listener delivered") { check(pressed == DialogInterface.BUTTON_POSITIVE) }
            test("image loader runs off main thread") { check(loadedOnWorker.get()) }
            test("deferred image delivers its pixels") {
                val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                deferred.setBounds(0, 0, 16, 16); deferred.draw(Canvas(bitmap))
                check(bitmap.getPixel(8, 8) == Color.RED)
            }
            android.util.Log.i("FlareChecks", "Limit " + results.joinToString(" | "))
            output.text = results.joinToString("\n")
        }, 500)
    }
}
