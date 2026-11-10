package app.flare.auto

import java.util.WeakHashMap

internal class SharedRequests<K, L : Any> {
    private val pending = HashMap<K, WeakHashMap<L, Int>>()
    fun isPending(key: K) = pending.containsKey(key)
    fun join(key: K, listener: L, generation: Int): Boolean {
        val first = !pending.containsKey(key)
        pending.getOrPut(key) { WeakHashMap() }[listener] = generation
        return first
    }
    fun leave(listener: L) { pending.values.forEach { it.remove(listener) } }
    fun complete(key: K): List<Pair<L, Int>> = pending.remove(key)?.entries?.map { it.key to it.value }.orEmpty()
}
