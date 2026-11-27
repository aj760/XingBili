package com.mytvb.core.common.cache

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OnDemandRequestCacheTest {
    @Test fun concurrentWaitersShareWorkAndOneCancellationIsIndependent() = runBlocking {
        val cache = OnDemandRequestCache<String, String>(2, 1000, this)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) { cache.getOrLoad("a") { calls++; gate.await(); "ok" } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { cache.getOrLoad("a") { error("duplicate") } }
        yield()
        first.cancelAndJoin()
        gate.complete(Unit)
        assertEquals("ok", second.await())
        assertEquals(1, calls)
    }
    @Test fun lastWaiterCancellationStopsWorkAndAllowsRetry() = runBlocking {
        val cache = OnDemandRequestCache<String, String>(2, 1000, this)
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val waiter = async { cache.getOrLoad("a") {
            try { started.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) }
        } }
        started.await()
        waiter.cancelAndJoin()
        withTimeout(1000) { stopped.await() }
        assertEquals("retry", cache.getOrLoad("a") { "retry" })
    }
    @Test fun ttlLruAndClearingInFlightAreDeterministic() = runBlocking {
        var now = 0L
        val cache = OnDemandRequestCache<String, String>(2, 10, this, { now })
        cache.getOrLoad("a") { "a" }; cache.getOrLoad("b") { "b" }
        cache.peek("a"); cache.getOrLoad("c") { "c" }
        assertNull(cache.peek("b"))
        now = 10
        assertNull(cache.peek("a"))
        val gate = CompletableDeferred<Unit>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { cache.getOrLoad("x") { gate.await(); "old" } }
        yield()
        cache.clearCompleted(); gate.complete(Unit)
        assertEquals("old", pending.await())
        assertNull(cache.peek("x"))
    }
    @Test fun failuresAreNotCachedAndZeroTtlOnlySharesInFlight() = runBlocking {
        val cache = OnDemandRequestCache<String, Result<String>>(2, 10, this, canCache = { it.isSuccess })
        cache.getOrLoad("a") { Result.failure(Exception()) }
        assertEquals("ok", cache.getOrLoad("a") { Result.success("ok") }.getOrThrow())
        val noCache = OnDemandRequestCache<String, String>(2, 0, this)
        assertEquals("one", noCache.getOrLoad("a") { "one" })
        assertEquals("two", noCache.getOrLoad("a") { "two" })
    }
}
