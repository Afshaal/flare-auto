package app.flare.auto
internal class BannerQueue<T>(private val key: (T) -> String) {
    private val pending = LinkedHashMap<String,T>()
    fun offer(value:T) {
        val id=key(value)
        if (!pending.containsKey(id) && pending.size>=3) pending.remove(pending.keys.first())
        pending[id]=value
    }
    fun remove(id:String) { pending.remove(id) }
    fun next():T? { val first=pending.entries.firstOrNull() ?: return null; pending.remove(first.key); return first.value }
    fun clear() = pending.clear()
}
