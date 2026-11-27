package com.mytvb.core.ui.base

import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.core.common.log.AppLog

object RecyclerViewPoolPrewarmer {
    private const val TAG = "PagePerf"
    private const val FRAME_DELAY_MS = 16L
    private const val DEFAULT_BUDGET_MS = 180L

    data class Plan(
        val count: Int,
        val budgetMs: Long,
        val maxPoolSize: Int = maxOf(count * 2, 12),
        val initialDelayMs: Long = 0L,
        val frameDelayMs: Long = FRAME_DELAY_MS,
        val stopWhenAdapterHasItems: Boolean = true
    ) {
        companion object {
            val Disabled = Plan(count = 0, budgetMs = 0L)
            val VideoFeed = Plan(
                count = 8,
                budgetMs = 220L,
                maxPoolSize = 60,
                initialDelayMs = 72L,
                frameDelayMs = FRAME_DELAY_MS,
                stopWhenAdapterHasItems = true
            )
            val DynamicFeed = Plan(
                count = 4,
                budgetMs = 140L,
                maxPoolSize = 60,
                initialDelayMs = 96L,
                frameDelayMs = FRAME_DELAY_MS,
                stopWhenAdapterHasItems = true
            )
            val CacheBackedList = Plan(count = 0, budgetMs = 0L)
        }
    }

    fun prewarm(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>,
        count: Int,
        source: String,
        pool: RecyclerView.RecycledViewPool = recyclerView.recycledViewPool,
        maxPoolSize: Int = maxOf(count * 2, 12),
        budgetMs: Long = DEFAULT_BUDGET_MS
    ) {
        prewarm(
            recyclerView = recyclerView,
            adapter = adapter,
            source = source,
            pool = pool,
            plan = Plan(
                count = count,
                budgetMs = budgetMs,
                maxPoolSize = maxPoolSize
            )
        )
    }

    fun prewarm(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>,
        source: String,
        pool: RecyclerView.RecycledViewPool = recyclerView.recycledViewPool,
        plan: Plan
    ) {
        if (plan.count <= 0 || plan.budgetMs <= 0L) return
        val viewType = runCatching { adapter.getItemViewType(0) }.getOrNull() ?: return
        pool.setMaxRecycledViews(viewType, plan.maxPoolSize)
        val startedAtMs = SystemClock.elapsedRealtime()
        AppLog.i(
            TAG,
            "$source holder_prewarm start count=${plan.count} budget=${plan.budgetMs}ms viewType=$viewType initialDelay=${plan.initialDelayMs}ms frameDelay=${plan.frameDelayMs}ms stopOnItems=${plan.stopWhenAdapterHasItems}"
        )
        prewarmOneByOne(
            recyclerView = recyclerView,
            adapter = adapter,
            pool = pool,
            viewType = viewType,
            source = source,
            startedAtMs = startedAtMs,
            index = 0,
            plan = plan
        )
    }

    private fun prewarmOneByOne(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>,
        pool: RecyclerView.RecycledViewPool,
        viewType: Int,
        source: String,
        startedAtMs: Long,
        index: Int,
        plan: Plan
    ) {
        val delayMs = if (index == 0) plan.initialDelayMs else plan.frameDelayMs
        recyclerView.postDelayed({
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            if (
                !recyclerView.isAttachedToWindow ||
                (plan.stopWhenAdapterHasItems && adapter.itemCount > 0) ||
                index >= plan.count ||
                elapsedMs >= plan.budgetMs
            ) {
                AppLog.i(
                    TAG,
                    "$source holder_prewarm stop created=$index elapsed=${elapsedMs}ms attached=${recyclerView.isAttachedToWindow} items=${adapter.itemCount} budget=${plan.budgetMs}ms"
                )
                return@postDelayed
            }
            val createStartMs = SystemClock.elapsedRealtime()
            runCatching {
                val holder = adapter.createViewHolder(recyclerView, viewType)
                pool.putRecycledView(holder)
            }.onFailure { throwable ->
                AppLog.w(TAG, "$source holder_prewarm failed index=$index ${throwable.message}")
                return@postDelayed
            }
            val createMs = SystemClock.elapsedRealtime() - createStartMs
            if (createMs > 8) {
                AppLog.i(TAG, "$source holder_prewarm create index=${index + 1}/${plan.count} elapsed=${createMs}ms")
            }
            prewarmOneByOne(
                recyclerView = recyclerView,
                adapter = adapter,
                pool = pool,
                viewType = viewType,
                source = source,
                startedAtMs = startedAtMs,
                index = index + 1,
                plan = plan
            )
        }, delayMs)
    }
}
