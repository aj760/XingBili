package com.mytvb.feature.player.danmaku

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mytvb.feature.player.danmaku.model.RenderSnapshot
import com.mytvb.feature.player.danmaku.model.SharedCacheEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Bitmap ownership, which local JVM tests cannot provide. */
@RunWith(AndroidJUnit4::class)
class RenderSnapshotCacheLeaseTest {

    @Test
    fun snapshotLeaseKeepsBitmapAliveAfterTheItemReleasesIt() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val entry = SharedCacheEntry(bitmap)
        entry.acquire() // The active item owns the first lease.

        val snapshot = RenderSnapshot()
        snapshot.ensureCapacity(1)
        assertTrue(entry.tryAcquire()) // Snapshot publication takes its independent lease.
        snapshot.cacheEntries[0] = entry
        snapshot.cacheGenerations[0] = 1
        snapshot.count = 1

        assertFalse(entry.release()) // Releasing the item must not invalidate the frame in flight.
        assertFalse(bitmap.isRecycled)

        snapshot.clear()
        assertTrue(entry.release()) // Once the snapshot retires, this was the final lease.
        bitmap.recycle()
    }
}
