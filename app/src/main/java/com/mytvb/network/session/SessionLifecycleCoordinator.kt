package com.mytvb.network.session

import com.mytvb.core.common.cache.OnDemandRequestCache
import kotlinx.coroutines.CancellationException

/** No Android/UI dependency: only a definitive anonymous/-101 probe may clear credentials. */
class SessionLifecycleCoordinator<T : Any>(
    private val hasCookie: () -> Boolean,
    private val hasRefreshToken: () -> Boolean,
    private val ownerVersion: () -> Long,
    private val probe: suspend () -> Probe<T>,
    private val refresh: suspend () -> Unit,
    private val accept: (T) -> Unit,
    private val clear: () -> Unit,
    private val lastChecked: () -> Long,
    private val markChecked: (Long) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = 24L * 60 * 60 * 1000
) {
    sealed interface Probe<out T> {
        data class Valid<T>(val user: T) : Probe<T>
        data object Expired : Probe<Nothing>
        data object Unavailable : Probe<Nothing>
    }

    enum class Outcome { LOGGED_IN, RECOVERED, EXPIRED_CLEARED, SKIP_NO_COOKIE, SKIP_THROTTLED, NETWORK_ERROR }
    private val checks = OnDemandRequestCache<Long, Outcome>(1, 0)
    private val recoveries = OnDemandRequestCache<Long, Outcome>(1, 0)

    suspend fun check(force: Boolean = false): Outcome {
        val owner = ownerVersion()
        return checks.getOrLoad(owner) {
            if (!hasCookie()) return@getOrLoad Outcome.SKIP_NO_COOKIE
            val now = nowMs()
            val last = lastChecked()
            if (!force && last > 0 && now >= last && now - last < intervalMs) {
                return@getOrLoad Outcome.SKIP_THROTTLED
            }
            val result = guarded(owner) {
                when (val response = probe()) {
                    is Probe.Valid -> {
                        requireOwner(owner)
                        accept(response.user)
                        Outcome.LOGGED_IN
                    }
                    Probe.Expired -> recover(owner)
                    Probe.Unavailable -> Outcome.NETWORK_ERROR
                }
            }
            if (result != Outcome.EXPIRED_CLEARED) requireOwner(owner)
            if (result == Outcome.LOGGED_IN || result == Outcome.RECOVERED) markChecked(now)
            result
        }
    }

    suspend fun recover(): Outcome = recover(ownerVersion())

    private suspend fun recover(owner: Long): Outcome = recoveries.getOrLoad(owner) {
        guarded(owner) {
            requireOwner(owner)
            if (!hasCookie()) return@guarded Outcome.SKIP_NO_COOKIE
            if (!hasRefreshToken()) {
                clear()
                return@guarded Outcome.EXPIRED_CLEARED
            }
            refresh()
            requireOwner(owner)
            when (val response = probe()) {
                is Probe.Valid -> {
                    requireOwner(owner)
                    accept(response.user)
                    Outcome.RECOVERED
                }
                Probe.Expired -> {
                    requireOwner(owner)
                    clear()
                    Outcome.EXPIRED_CLEARED
                }
                Probe.Unavailable -> Outcome.NETWORK_ERROR
            }
        }
    }

    private suspend fun guarded(owner: Long, block: suspend () -> Outcome): Outcome = try {
        requireOwner(owner)
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        requireOwner(owner)
        Outcome.NETWORK_ERROR
    }

    private fun requireOwner(owner: Long) {
        if (owner != ownerVersion()) throw StaleSessionException()
    }
}
