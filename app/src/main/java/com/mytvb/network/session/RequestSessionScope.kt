package com.mytvb.network.session

import kotlinx.coroutines.CancellationException

/** Opaque process-local identity. Credentials never enter cache keys or diagnostics. */
data class RequestSessionSnapshot(val generation: Long)

class SessionScopeTracker {
    private var inputs: List<String>? = null
    private var generation = 0L

    @Synchronized
    fun capture(credentials: List<String>): RequestSessionSnapshot {
        if (inputs != credentials) {
            inputs = credentials.toList()
            generation++
        }
        return RequestSessionSnapshot(generation)
    }

    /** Also records A -> B -> A transitions between two capture calls. */
    @Synchronized
    fun invalidate() {
        generation++
    }
}

class StaleSessionException : CancellationException("Request belongs to an obsolete session")

object RequestSessionScope {
    private val tracker = SessionScopeTracker()

    @Volatile
    private var credentials: () -> List<String> = { emptyList() }

    fun configure(provider: () -> List<String>) {
        credentials = provider
        tracker.invalidate()
    }

    fun capture(): RequestSessionSnapshot = tracker.capture(credentials())

    fun invalidate() = tracker.invalidate()

    fun isCurrent(snapshot: RequestSessionSnapshot): Boolean = snapshot == capture()

    fun requireCurrent(snapshot: RequestSessionSnapshot) {
        if (!isCurrent(snapshot)) throw StaleSessionException()
    }
}
