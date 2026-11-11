package app.flare.auto

internal object UpdateVersion {
    private val version = Regex("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
    private fun parse(value: String): Pair<List<Int>, String?>? {
        val m = version.matchEntire(value.trim()) ?: return null
        val numbers = (1..3).map { m.groupValues[it].ifEmpty { "0" }.toIntOrNull() ?: return null }
        return numbers to m.groupValues[4].ifEmpty { null }
    }
    fun compare(a: String, b: String): Int? {
        val left = parse(a) ?: return null
        val right = parse(b) ?: return null
        for (i in 0..2) if (left.first[i] != right.first[i]) return left.first[i].compareTo(right.first[i])
        val x = left.second ?: return if (right.second == null) 0 else 1
        val y = right.second ?: return -1
        val xs = x.split('.')
        val ys = y.split('.')
        for (i in 0 until maxOf(xs.size, ys.size)) {
            if (i >= xs.size) return -1
            if (i >= ys.size) return 1
            val xn = xs[i].toIntOrNull(); val yn = ys[i].toIntOrNull()
            val c = when { xn != null && yn != null -> xn.compareTo(yn); xn != null -> -1; yn != null -> 1; else -> xs[i].compareTo(ys[i]) }
            if (c != 0) return c
        }
        return 0
    }
}

/** Installed versionName, for User-Agent strings. */
internal fun appVersion(context: android.content.Context): String =
    try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" } catch (_: Exception) { "unknown" }
