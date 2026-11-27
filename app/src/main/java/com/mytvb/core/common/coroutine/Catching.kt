package com.mytvb.core.common.coroutine

import kotlinx.coroutines.CancellationException

/** Cancellation is control flow, not a failed request or a reason to retry. */
inline fun <T> catchingRequest(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    var cause: Throwable? = error
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    while (cause != null && visited.add(cause)) {
        if (cause is CancellationException) throw cause
        cause = cause.cause
    }
    Result.failure(error)
}
