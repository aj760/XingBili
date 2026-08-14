package blbl.cat3399.core.paging

/**
 * A small pagination + concurrency state machine for "grid list" screens.
 *
 * Goals (see docs/rebuild.md Step 1):
 * - Unify isLoading/endReached state transitions
 * - Ensure stale in-flight requests never apply (generation token)
 * - Support both page-number and cursor-style pagination via a generic [K] key
 *
 * This class is intentionally Android-free and unit-test friendly.
 */
class PagedGridStateMachine<K>(
    private val initialKey: K,
) {
    data class State<K>(
        val nextKey: K,
        val isLoading: Boolean,
        val endReached: Boolean,
        val generation: Long,
    )

    data class Update<K, Item>(
        val items: List<Item>,
        val nextKey: K,
        val endReached: Boolean,
    )

    sealed interface LoadResult<out Item> {
        val isRefresh: Boolean

        data class Applied<Item>(
            val items: List<Item>,
            override val isRefresh: Boolean,
        ) : LoadResult<Item>

        data class Skipped(
            override val isRefresh: Boolean,
            val reason: Reason,
        ) : LoadResult<Nothing> {
            enum class Reason {
                AlreadyLoading,
                EndReached,
            }
        }

        /**
         * Fetch was intentionally aborted (e.g. login required) and no state advanced.
         */
        data class Aborted(
            override val isRefresh: Boolean,
        ) : LoadResult<Nothing>

        /**
         * Fetch finished, but this request is stale due to a [reset] occurring in-between.
         */
        data class IgnoredStale(
            override val isRefresh: Boolean,
        ) : LoadResult<Nothing>
    }

    private val lock = Any()

    @Volatile
    private var state: State<K> =
        State(
            nextKey = initialKey,
            isLoading = false,
            endReached = false,
            generation = 0L,
        )

    fun snapshot(): State<K> = state

    fun reset() {
        resetTo(initialKey)
    }

    fun resetTo(nextKey: K) {
        synchronized(lock) {
            state =
                State(
                    nextKey = nextKey,
                    isLoading = false,
                    endReached = false,
                    generation = state.generation + 1,
                )
        }
    }

    /**
     * Load the "next page" based on current [State.nextKey].
     *
     * - If already loading: returns [LoadResult.Skipped] and does not call [fetch]
     * - If end reached: returns [LoadResult.Skipped] and does not call [fetch]
     * - If [reset] happens while a request is in-flight, the old request finishes with
     *   [LoadResult.IgnoredStale] and does not affect state.
     *
     * The [fetch] phase should avoid mutating external state (e.g. "loadedIds" sets).
     * Put filtering/dedup logic into [reduce] and apply side effects only when receiving
     * an [LoadResult.Applied] in the caller.
     */
    suspend fun <Fetched, Item> loadNextPage(
        isRefresh: Boolean,
        fetch: suspend (key: K) -> Fetched?,
        reduce: (key: K, fetched: Fetched) -> Update<K, Item>,
    ): LoadResult<Item> {
        val generationAtStart: Long
        val keyAtStart: K
        synchronized(lock) {
            val s = state
            if (s.endReached) {
                return LoadResult.Skipped(isRefresh = isRefresh, reason = LoadResult.Skipped.Reason.EndReached)
            }
            if (s.isLoading) {
                return LoadResult.Skipped(isRefresh = isRefresh, reason = LoadResult.Skipped.Reason.AlreadyLoading)
            }
            generationAtStart = s.generation
            keyAtStart = s.nextKey
            state = s.copy(isLoading = true)
        }

        try {
            val fetched = fetch(keyAtStart)
            if (fetched == null) {
                synchronized(lock) {
                    val s = state
                    if (s.generation == generationAtStart) {
                        state = s.copy(isLoading = false)
                    }
                }
                return LoadResult.Aborted(isRefresh = isRefresh)
            }

            val update = reduce(keyAtStart, fetched)
            synchronized(lock) {
                val s = state
                if (s.generation != generationAtStart) {
                    return LoadResult.IgnoredStale(isRefresh = isRefresh)
                }
                state =
                    s.copy(
                        nextKey = update.nextKey,
                        endReached = update.endReached,
                        isLoading = false,
                    )
            }
            return LoadResult.Applied(items = update.items, isRefresh = isRefresh)
        } catch (t: Throwable) {
            synchronized(lock) {
                val s = state
                if (s.generation == generationAtStart) {
                    state = s.copy(isLoading = false)
                }
            }
            throw t
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <Item> PagedGridStateMachine.LoadResult<Item>.appliedOrNull(): PagedGridStateMachine.LoadResult.Applied<Item>? {
    return this as? PagedGridStateMachine.LoadResult.Applied<Item>
}
