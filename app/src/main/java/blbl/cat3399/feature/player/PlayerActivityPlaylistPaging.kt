package blbl.cat3399.feature.player

import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.ui.AppToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private typealias PlaylistLoadCallback = (Boolean) -> Unit

internal fun PlayerActivity.hasMorePlaylistItems(kind: PlayerVideoListKind): Boolean {
    return when (kind) {
        PlayerVideoListKind.PAGE -> pageListContinuation?.hasMore == true
        PlayerVideoListKind.PARTS -> partsListContinuation?.hasMore == true
        PlayerVideoListKind.RECOMMEND -> false
    }
}

internal fun PlayerActivity.isPlaylistLoadMoreRunning(kind: PlayerVideoListKind): Boolean {
    return when (kind) {
        PlayerVideoListKind.PAGE -> pageListLoadMoreJob?.isActive == true
        PlayerVideoListKind.PARTS -> partsListLoadMoreJob?.isActive == true
        PlayerVideoListKind.RECOMMEND -> false
    }
}

internal fun PlayerActivity.preloadNextPageForPlaylist(kind: PlayerVideoListKind) {
    if (!hasMorePlaylistItems(kind)) return
    loadMorePlaylist(kind = kind)
}

internal fun PlayerActivity.ensurePlaylistIndexLoaded(
    kind: PlayerVideoListKind,
    index: Int,
    onComplete: (Boolean) -> Unit,
) {
    val currentSize =
        when (kind) {
            PlayerVideoListKind.PAGE -> pageListItems.size
            PlayerVideoListKind.PARTS -> partsListItems.size
            PlayerVideoListKind.RECOMMEND -> 0
        }
    if (index in 0 until currentSize) {
        onComplete(true)
        return
    }
    if (!hasMorePlaylistItems(kind)) {
        onComplete(false)
        return
    }

    loadMorePlaylist(kind = kind) { appended ->
        if (!appended) {
            onComplete(
                when (kind) {
                    PlayerVideoListKind.PAGE -> index in pageListItems.indices
                    PlayerVideoListKind.PARTS -> index in partsListItems.indices
                    PlayerVideoListKind.RECOMMEND -> false
                },
            )
            return@loadMorePlaylist
        }
        ensurePlaylistIndexLoaded(kind = kind, index = index, onComplete = onComplete)
    }
}

internal fun PlayerActivity.loadMorePlaylist(
    kind: PlayerVideoListKind,
    onComplete: PlaylistLoadCallback = {},
) {
    if (kind == PlayerVideoListKind.RECOMMEND) {
        onComplete(false)
        return
    }

    val continuation =
        when (kind) {
            PlayerVideoListKind.PAGE -> pageListContinuation
            PlayerVideoListKind.PARTS -> partsListContinuation
            PlayerVideoListKind.RECOMMEND -> null
        }
    if (continuation?.hasMore != true) {
        onComplete(false)
        return
    }

    val callbacks = callbacksForPlaylist(kind)
    callbacks.add(onComplete)
    if (isPlaylistLoadMoreRunning(kind)) return

    val job =
        lifecycleScope.launch {
            var appended = false
            try {
                while (true) {
                    val parsed =
                        withContext(Dispatchers.IO) {
                            continuation.loadNextPage()
                        }
                    if (parsed.items.isNotEmpty()) {
                        applyPlaylistAppend(kind = kind, parsed = parsed)
                        appended = true
                        break
                    }
                    if (!continuation.hasMore) break
                }
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                AppLog.e("Player", "playlist:loadMore failed kind=$kind", t)
                if (isBottomCardPanelVisible() && bottomCardPanelKind == kind) {
                    AppToast.show(this@loadMorePlaylist, playlistLoadMoreFailureMessage(kind = kind, throwable = t))
                }
            } finally {
                when (kind) {
                    PlayerVideoListKind.PAGE -> pageListLoadMoreJob = null
                    PlayerVideoListKind.PARTS -> partsListLoadMoreJob = null
                    PlayerVideoListKind.RECOMMEND -> Unit
                }
                if (kind == PlayerVideoListKind.PAGE) {
                    syncPagePlaylistToken()
                }
                val pending = ArrayList(callbacks)
                callbacks.clear()
                pending.forEach { it(appended) }
            }
        }

    when (kind) {
        PlayerVideoListKind.PAGE -> pageListLoadMoreJob = job
        PlayerVideoListKind.PARTS -> partsListLoadMoreJob = job
        PlayerVideoListKind.RECOMMEND -> Unit
    }
}

private fun PlayerActivity.applyPlaylistAppend(kind: PlayerVideoListKind, parsed: PlaylistParsed) {
    when (kind) {
        PlayerVideoListKind.PAGE -> {
            pageListItems = pageListItems + parsed.items
            pageListUiCards = pageListUiCards + parsed.uiCards
            syncPagePlaylistToken()
            updatePlaylistControls()
            notifyPageListPanelChanged()
        }

        PlayerVideoListKind.PARTS -> {
            partsListItems = partsListItems + parsed.items
            partsListUiCards = partsListUiCards + parsed.uiCards
            updatePlaylistControls()
            notifyPartsListPanelChanged()
        }

        PlayerVideoListKind.RECOMMEND -> Unit
    }
}

private fun PlayerActivity.syncPagePlaylistToken() {
    val token = pageListToken?.takeIf { it.isNotBlank() } ?: return
    PlayerPlaylistStore.sync(
        token = token,
        items = pageListItems,
        uiCards = pageListUiCards,
        continuation = pageListContinuation,
    )
}

private fun PlayerActivity.callbacksForPlaylist(kind: PlayerVideoListKind): ArrayList<PlaylistLoadCallback> {
    return when (kind) {
        PlayerVideoListKind.PAGE -> pageListLoadMoreCallbacks
        PlayerVideoListKind.PARTS -> partsListLoadMoreCallbacks
        PlayerVideoListKind.RECOMMEND -> pageListLoadMoreCallbacks
    }
}

private fun PlayerActivity.playlistLoadMoreFailureMessage(
    kind: PlayerVideoListKind,
    throwable: Throwable,
): String {
    val defaultMsg =
        when (kind) {
            PlayerVideoListKind.PAGE -> "加载更多视频失败"
            PlayerVideoListKind.PARTS -> "加载更多合集/分P失败"
            PlayerVideoListKind.RECOMMEND -> "加载更多推荐失败"
        }
    return throwable.message?.takeIf { it.isNotBlank() } ?: defaultMsg
}
