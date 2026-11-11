package app.flare.auto

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle

internal class EditorContext(base:Context,val draft:EditorDraft,val images:java.io.File?=null):ContextWrapper(base)
internal class EditorDraft(source:SharedPreferences):SharedPreferences {
    private val original=HashMap(source.all)
    private val values=HashMap<String,Any?>().apply { putAll(source.all) }
    fun save(target:SharedPreferences) { val e=target.edit(); original.keys.filter { !values.containsKey(it) }.forEach { e.remove(it) };values.forEach { (k,v)->if(original[k]!=v) put(e,k,v) };e.apply() }
    /** Saved instance state: the values plus a type index ("<type char><key>") so they read back with typed getters. */
    fun bundle()=Bundle().apply {
        val types=ArrayList<String>()
        values.forEach { (k,v)-> when(v) {
            is String->{ putString(k,v);types.add("s$k") }; is Int->{ putInt(k,v);types.add("i$k") }; is Long->{ putLong(k,v);types.add("l$k") }
            is Float->{ putFloat(k,v);types.add("f$k") }; is Boolean->{ putBoolean(k,v);types.add("b$k") }
            is Set<*>->{ putStringArrayList(k,ArrayList(v.filterIsInstance<String>()));types.add("S$k") }
        } }
        putStringArrayList(TYPES,types)
    }
    fun restore(bundle:Bundle) {
        values.clear()
        bundle.getStringArrayList(TYPES)?.forEach { entry-> val k=entry.substring(1); values[k]=when(entry[0]) {
            's'->bundle.getString(k); 'i'->bundle.getInt(k); 'l'->bundle.getLong(k); 'f'->bundle.getFloat(k); 'b'->bundle.getBoolean(k)
            else->bundle.getStringArrayList(k)?.toSet()
        } }
    }
    override fun getAll():MutableMap<String,*> = HashMap(values)
    override fun contains(key:String?)=values.containsKey(key)
    override fun getString(key:String?,default:String?)=values[key] as? String ?: default
    override fun getStringSet(key:String?,default:MutableSet<String>?):MutableSet<String>?=(values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: default
    override fun getInt(key:String?,default:Int)=values[key] as? Int ?: default
    override fun getLong(key:String?,default:Long)=values[key] as? Long ?: default
    override fun getFloat(key:String?,default:Float)=values[key] as? Float ?: default
    override fun getBoolean(key:String?,default:Boolean)=values[key] as? Boolean ?: default
    override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener?)=Unit
    override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener?)=Unit
    override fun edit():SharedPreferences.Editor=object:SharedPreferences.Editor {
        private val changes=HashMap<String,Any?>(); private var clear=false
        override fun putString(k:String?,v:String?)=apply { if(k!=null) changes[k]=v }
        override fun putStringSet(k:String?,v:MutableSet<String>?)=apply { if(k!=null) changes[k]=v?.toSet() }
        override fun putInt(k:String?,v:Int)=apply { if(k!=null) changes[k]=v }
        override fun putLong(k:String?,v:Long)=apply { if(k!=null) changes[k]=v }
        override fun putFloat(k:String?,v:Float)=apply { if(k!=null) changes[k]=v }
        override fun putBoolean(k:String?,v:Boolean)=apply { if(k!=null) changes[k]=v }
        override fun remove(k:String?)=apply { if(k!=null) changes[k]=null }
        override fun clear()=apply { clear=true }
        override fun commit():Boolean { if(clear) values.clear(); changes.forEach { (k,v)->if(v==null) values.remove(k) else values[k]=v }; return true }
        override fun apply() { commit() }
    }
    private fun put(e:SharedPreferences.Editor,k:String,v:Any?) { when(v) {
        is String->e.putString(k,v); is Int->e.putInt(k,v); is Long->e.putLong(k,v); is Float->e.putFloat(k,v); is Boolean->e.putBoolean(k,v)
        is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())
    } }
    private companion object { const val TYPES="__editor_draft_types" }
}
