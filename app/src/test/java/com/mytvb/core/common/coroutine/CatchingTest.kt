package com.mytvb.core.common.coroutine

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CatchingTest {
    @Test fun ordinaryErrorsBecomeFailures() {
        assertTrue(catchingRequest { throw IOException() }.isFailure)
    }
    @Test fun cancellationIncludingWrappedCancellationEscapes() {
        val cancelled = CancellationException("cancelled")
        for (error in listOf(cancelled, IOException("wrapped", cancelled))) {
            try {
                catchingRequest { throw error }
                fail("Cancellation swallowed")
            } catch (actual: CancellationException) { assertSame(cancelled, actual) }
        }
    }
}
