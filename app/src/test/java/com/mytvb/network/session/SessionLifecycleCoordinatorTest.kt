package com.mytvb.network.session

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SessionLifecycleCoordinatorTest {
    private class Fixture {
        var owner = 0L
        var cleared = 0
        var accepted = 0
        var checked = 0L
        var probes = 0
        var probe: suspend () -> SessionLifecycleCoordinator.Probe<String> = {
            SessionLifecycleCoordinator.Probe.Valid("user")
        }
        var refresh: suspend () -> Unit = {}
        val coordinator = SessionLifecycleCoordinator(
            hasCookie = { true },
            hasRefreshToken = { true },
            ownerVersion = { owner },
            probe = { probes++; probe() },
            refresh = { refresh() },
            accept = { _: String -> accepted++ },
            clear = { cleared++; owner++ },
            lastChecked = { checked },
            markChecked = { checked = it },
            nowMs = { 1000L }
        )
    }

    @Test fun validProbeIsThrottledAndForcedProbeStillRuns() = runBlocking {
        val f = Fixture()
        assertEquals(SessionLifecycleCoordinator.Outcome.LOGGED_IN, f.coordinator.check())
        assertEquals(SessionLifecycleCoordinator.Outcome.SKIP_THROTTLED, f.coordinator.check())
        assertEquals(SessionLifecycleCoordinator.Outcome.LOGGED_IN, f.coordinator.check(true))
        assertEquals(2, f.probes)
    }
    @Test fun unknownAndNetworkFailuresNeverClearCredentials() = runBlocking {
        val f = Fixture()
        f.probe = { SessionLifecycleCoordinator.Probe.Unavailable }
        assertEquals(SessionLifecycleCoordinator.Outcome.NETWORK_ERROR, f.coordinator.check())
        f.probe = { throw IOException("offline") }
        assertEquals(SessionLifecycleCoordinator.Outcome.NETWORK_ERROR, f.coordinator.check())
        assertEquals(0, f.cleared)
        assertEquals(0L, f.checked)
    }
    @Test fun cancellationNeverClearsCredentials() = runBlocking {
        val f = Fixture()
        f.refresh = { throw CancellationException("cancelled") }
        try { f.coordinator.recover(); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
        assertEquals(0, f.cleared)
    }
    @Test fun onlyDefinitiveExpiryAfterRefreshClears() = runBlocking {
        val f = Fixture()
        f.probe = { SessionLifecycleCoordinator.Probe.Expired }
        assertEquals(SessionLifecycleCoordinator.Outcome.EXPIRED_CLEARED, f.coordinator.check())
        assertEquals(1, f.cleared)
    }
    @Test fun accountSwitchDuringProbeDiscardsOldUser() = runBlocking {
        val f = Fixture()
        f.probe = { f.owner++; SessionLifecycleCoordinator.Probe.Valid("old-user") }
        try { f.coordinator.check(); fail("Old account accepted") }
        catch (_: StaleSessionException) { }
        assertEquals(0, f.accepted)
        assertEquals(0, f.cleared)
    }
    @Test fun overlappingChecksShareOneProbe() = runBlocking {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        f.probe = { entered.complete(Unit); gate.await(); SessionLifecycleCoordinator.Probe.Valid("user") }
        val first = async(start = CoroutineStart.UNDISPATCHED) { f.coordinator.check() }
        entered.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { f.coordinator.check() }
        gate.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, f.probes)
    }
}
