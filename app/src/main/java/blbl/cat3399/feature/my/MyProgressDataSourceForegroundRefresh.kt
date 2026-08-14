package blbl.cat3399.feature.my

internal interface MyProgressDataSourceForegroundRefreshTarget {
    fun onMyProgressDataSourceForeground(epoch: Long)
}

internal class ForegroundRefreshEpochTracker {
    private var hasEnteredForeground: Boolean = false
    private var epoch: Long = 0L

    fun onForegroundEntry(): Long {
        if (!hasEnteredForeground) {
            hasEnteredForeground = true
            return 0L
        }
        epoch += 1L
        return epoch
    }

    fun currentEpoch(): Long = epoch
}

internal class ForegroundRefreshEpochConsumer {
    private var handledEpoch: Long = 0L

    fun shouldRefresh(epoch: Long, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        if (epoch <= 0L) return false
        if (handledEpoch >= epoch) return false
        handledEpoch = epoch
        return true
    }
}
