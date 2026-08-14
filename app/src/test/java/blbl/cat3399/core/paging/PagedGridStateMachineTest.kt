package blbl.cat3399.core.paging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedGridStateMachineTest {
    @Test
    fun reset_should_ignore_in_flight_request() =
        runTest {
            val machine = PagedGridStateMachine(initialKey = 1)
            val started = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()

            val job =
                launch {
                    val result =
                        machine.loadNextPage(
                            isRefresh = true,
                            fetch = { key ->
                                started.complete(Unit)
                                gate.await()
                                key
                            },
                            reduce = { key, _ ->
                                PagedGridStateMachine.Update(
                                    items = listOf("item"),
                                    nextKey = key + 1,
                                    endReached = false,
                                )
                            },
                        )

                    assertTrue(result is PagedGridStateMachine.LoadResult.IgnoredStale)
                }

            started.await()
            machine.reset()
            gate.complete(Unit)
            job.join()

            val s = machine.snapshot()
            assertEquals(1, s.nextKey)
            assertFalse(s.isLoading)
            assertFalse(s.endReached)
        }

    @Test
    fun end_reached_should_skip_load() =
        runTest {
            val machine = PagedGridStateMachine(initialKey = 1)
            var fetchCount = 0

            val first =
                machine.loadNextPage(
                    isRefresh = true,
                    fetch = { key ->
                        fetchCount++
                        key
                    },
                    reduce = { key, _ ->
                        PagedGridStateMachine.Update(
                            items = emptyList<String>(),
                            nextKey = key,
                            endReached = true,
                        )
                    },
                )
            assertTrue(first is PagedGridStateMachine.LoadResult.Applied)

            val second =
                machine.loadNextPage(
                    isRefresh = false,
                    fetch = {
                        fetchCount++
                        it
                    },
                    reduce = { key, _ ->
                        PagedGridStateMachine.Update(
                            items = listOf("never"),
                            nextKey = key + 1,
                            endReached = false,
                        )
                    },
                )
            assertTrue(second is PagedGridStateMachine.LoadResult.Skipped)
            assertEquals(1, fetchCount)
        }

    @Test
    fun exception_should_not_lock_state_machine() =
        runTest {
            val machine = PagedGridStateMachine(initialKey = 1)
            var attempts = 0

            runCatching {
                machine.loadNextPage(
                    isRefresh = false,
                    fetch = {
                        attempts++
                        error("boom")
                    },
                    reduce = { key, _ ->
                        PagedGridStateMachine.Update(
                            items = emptyList<Unit>(),
                            nextKey = key,
                            endReached = false,
                        )
                    },
                )
            }
            assertEquals(1, attempts)

            val s1 = machine.snapshot()
            assertEquals(1, s1.nextKey)
            assertFalse(s1.isLoading)
            assertFalse(s1.endReached)

            val ok =
                machine.loadNextPage(
                    isRefresh = false,
                    fetch = { key ->
                        attempts++
                        key
                    },
                    reduce = { key, _ ->
                        PagedGridStateMachine.Update(
                            items = listOf("ok"),
                            nextKey = key + 1,
                            endReached = false,
                        )
                    },
                )
            assertTrue(ok is PagedGridStateMachine.LoadResult.Applied)

            val s2 = machine.snapshot()
            assertEquals(2, s2.nextKey)
            assertFalse(s2.isLoading)
        }
}

