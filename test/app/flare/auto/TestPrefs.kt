package app.flare.auto

import android.content.SharedPreferences

/** In-memory SharedPreferences for JVM tests (android.jar only ships stubs). */
class TestPrefs(vararg initial: Pair<String, Any>) : SharedPreferences {
    private val values = HashMap<String, Any?>(initial.toMap())

    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun contains(key: String?) = values.containsKey(key)
    override fun getString(key: String?, default: String?) = values[key] as? String ?: default
    override fun getStringSet(key: String?, default: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: default
    override fun getInt(key: String?, default: Int) = values[key] as? Int ?: default
    override fun getLong(key: String?, default: Long) = values[key] as? Long ?: default
    override fun getFloat(key: String?, default: Float) = values[key] as? Float ?: default
    override fun getBoolean(key: String?, default: Boolean) = values[key] as? Boolean ?: default
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = HashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?) = apply { changes[key] = value?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            return true
        }
        override fun apply() { commit() }
    }
}
