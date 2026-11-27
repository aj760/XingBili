package com.mytvb.core.common.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Bounded LRU/TTL cache with shared in-flight work. A consumer owns only its wait;
 * the last departing consumer cancels the request. MediaSource/Player ownership
 * deliberately does not belong here.
 */
class OnDemandRequestCache<K : Any, V>(
    private val maxEntries: Int,
    private val maxAgeMs: Long,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val canCache: (V) -> Boolean = { true }
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)
    private class Flight<V> {
        lateinit var work: Deferred<V>
        var waiters = 0
    }

    private val lock = Any()
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    private val flights = mutableMapOf<K, Flight<V>>()
    private var generation = 0L

    init {
        require(maxEntries > 0)
        require(maxAgeMs >= 0)
    }

    fun peek(key: K): V? = synchronized(lock) {
        val entry = entries[key] ?: return@synchronized null
        if (nowMs() >= entry.expiresAt) {
            entries.remove(key)
            null
        } else entry.value
    }

    suspend fun getOrLoad(key: K, load: suspend () -> V): V {
        currentCoroutineContext().ensureActive()
        val flight = synchronized(lock) {
            val cached = entries[key]
            if (cached != null && nowMs() < cached.expiresAt) return cached.value
            entries.remove(key)
            val selected = flights[key] ?: Flight<V>().also { created ->
                val startedIn = generation
                created.work = scope.async(start = CoroutineStart.LAZY) {
                    val value = load()
                    currentCoroutineContext().ensureActive()
                    synchronized(lock) {
                        if (maxAgeMs > 0 && generation == startedIn &&
                            flights[key] === created && canCache(value)
                        ) {
                            entries[key] = Entry(value, nowMs() + maxAgeMs)
                            while (entries.size > maxEntries) entries.remove(entries.keys.first())
                        }
                    }
                    value
                }
                flights[key] = created
            }
            selected.waiters++
            selected
        }
        return try {
            val value = flight.work.await()
            currentCoroutineContext().ensureActive()
            value
        } finally {
            synchronized(lock) {
                if (--flight.waiters == 0) {
                    if (flights[key] === flight) flights.remove(key)
                    if (!flight.work.isCompleted) flight.work.cancel()
                }
            }
        }
    }

    fun invalidate(key: K) = synchronized(lock) {
        entries.remove(key)
        flights.remove(key)?.work?.cancel()
        Unit
    }

    /** Existing waiters may finish, but their old result must not repopulate the cache. */
    fun clearCompleted() = synchronized(lock) {
        entries.clear()
        generation++
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        generation++
        flights.values.forEach { it.work.cancel() }
        flights.clear()
    }
}
