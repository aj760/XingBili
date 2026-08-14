package blbl.cat3399.feature.following

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.ui.postDelayedIfAlive
import blbl.cat3399.databinding.ItemUpSectionBinding
import blbl.cat3399.feature.video.VideoCardAdapter
import kotlin.math.roundToInt

enum class UpDetailSectionKind { SEASON, SERIES }

data class UpDetailSection(
    val kind: UpDetailSectionKind,
    val id: Long,
    val title: String,
    val totalCount: Int?,
    val videos: List<VideoCard>,
) {
    val stableId: String
        get() =
            when (kind) {
                UpDetailSectionKind.SEASON -> "season:$id"
                UpDetailSectionKind.SERIES -> "series:$id"
            }
}

class UpDetailSectionAdapter(
    private val onVideoClick: (section: UpDetailSection, card: VideoCard, index: Int) -> Unit,
    private val onRequestLoadMore: (section: UpDetailSection, requestedNextIndex: Int) -> Unit,
    private val onRequestMoveToTabs: (() -> Boolean)? = null,
) : RecyclerView.Adapter<UpDetailSectionAdapter.Vh>() {
    private val items = ArrayList<UpDetailSection>()
    private var attachedRecyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    fun replaceAll(list: List<UpDetailSection>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun appendSections(list: List<UpDetailSection>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun appendVideos(sectionStableId: String, videos: List<VideoCard>) {
        if (videos.isEmpty()) return
        val pos = items.indexOfFirst { it.stableId == sectionStableId }
        if (pos < 0) return
        val old = items[pos]
        val updated = old.copy(videos = old.videos + videos)
        items[pos] = updated

        val holder = attachedRecyclerView?.findViewHolderForAdapterPosition(pos) as? Vh
        if (holder != null) {
            holder.appendVideos(updatedSection = updated, newVideos = videos)
        } else {
            notifyItemChanged(pos)
        }
    }

    fun requestVideoFocus(sectionStableId: String, index: Int): Boolean {
        val rv = attachedRecyclerView ?: return false
        val pos = items.indexOfFirst { it.stableId == sectionStableId }
        if (pos < 0) return false
        val holder = rv.findViewHolderForAdapterPosition(pos) as? Vh ?: return false
        holder.requestVideoFocus(index)
        return true
    }

    fun snapshot(): List<UpDetailSection> = items.toList()

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemId(position: Int): Long = items[position].stableId.hashCode().toLong()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        attachedRecyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemUpSectionBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding, onVideoClick, onRequestLoadMore, onRequestMoveToTabs)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class Vh(
        private val binding: ItemUpSectionBinding,
        private val onVideoClick: (section: UpDetailSection, card: VideoCard, index: Int) -> Unit,
        private val onRequestLoadMore: (section: UpDetailSection, requestedNextIndex: Int) -> Unit,
        private val onRequestMoveToTabs: (() -> Boolean)?,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val videoAdapter: VideoCardAdapter
        private var boundSection: UpDetailSection? = null

        init {
            videoAdapter =
                VideoCardAdapter(
                    onClick = { card, index ->
                        val section = boundSection ?: return@VideoCardAdapter
                        onVideoClick(section, card, index)
                    },
                    onLongClick = null,
                    fixedItemWidthDimenRes = R.dimen.video_detail_section_card_width,
                    fixedItemMarginDimenRes = null,
                    showWatchedBadge = BiliClient.prefs::watchedBadgeUpMasterSpace,
                )

            binding.recyclerVideos.layoutManager =
                LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
            binding.recyclerVideos.itemAnimator = null
            binding.recyclerVideos.adapter = videoAdapter

            installHorizontalVideoFocusHandlers()
        }

        fun bind(section: UpDetailSection) {
            boundSection = section
            val kindPrefix =
                when (section.kind) {
                    UpDetailSectionKind.SEASON -> binding.root.context.getString(R.string.up_tab_collection)
                    UpDetailSectionKind.SERIES -> binding.root.context.getString(R.string.up_tab_series)
                }
            val titleText =
                if (section.totalCount != null && section.totalCount > 0) {
                    "${kindPrefix}：${section.title}（${section.totalCount}）"
                } else {
                    "${kindPrefix}：${section.title}"
                }
            binding.tvTitle.text = titleText
            videoAdapter.submit(section.videos)
        }

        fun appendVideos(updatedSection: UpDetailSection, newVideos: List<VideoCard>) {
            boundSection = updatedSection
            videoAdapter.append(newVideos)
        }

        fun requestVideoFocus(index: Int) {
            requestVideoFocus(position = index, attempt = 0)
        }

        private fun installHorizontalVideoFocusHandlers() {
            val recycler = binding.recyclerVideos
            recycler.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        view.setOnKeyListener { _, keyCode, event ->
                            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                            val holder = recycler.findContainingViewHolder(view)
                            val pos =
                                holder?.bindingAdapterPosition
                                    ?.takeIf { it != RecyclerView.NO_POSITION }
                                    ?: return@setOnKeyListener false
                            val total = recycler.adapter?.itemCount ?: 0

                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (total <= 0) return@setOnKeyListener true
                                    if (pos >= total - 1) {
                                        val section = boundSection ?: return@setOnKeyListener true
                                        onRequestLoadMore(section, pos + 1)
                                        return@setOnKeyListener true
                                    }

                                    val itemView = recycler.findContainingItemView(view) ?: view
                                    val next =
                                        FocusFinder.getInstance().findNextFocus(recycler, itemView, View.FOCUS_RIGHT)
                                    if (next != null && FocusTreeUtils.isDescendantOf(next, recycler)) {
                                        if (next.requestFocus()) return@setOnKeyListener true
                                    }

                                    if (recycler.canScrollHorizontally(1)) {
                                        val dx = (itemView.width * 0.8f).roundToInt().coerceAtLeast(1)
                                        recycler.scrollBy(dx, 0)
                                    }
                                    recycler.postIfAlive(isAlive = { bindingAdapterPosition != RecyclerView.NO_POSITION }) {
                                        requestVideoFocus(position = pos + 1, attempt = 0)
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (total <= 0) return@setOnKeyListener true
                                    if (pos <= 0) return@setOnKeyListener true

                                    val itemView = recycler.findContainingItemView(view) ?: view
                                    val next =
                                        FocusFinder.getInstance().findNextFocus(recycler, itemView, View.FOCUS_LEFT)
                                    if (next != null && FocusTreeUtils.isDescendantOf(next, recycler)) {
                                        if (next.requestFocus()) return@setOnKeyListener true
                                    }

                                    if (recycler.canScrollHorizontally(-1)) {
                                        val dx = (itemView.width * 0.8f).roundToInt().coerceAtLeast(1)
                                        recycler.scrollBy(-dx, 0)
                                    }
                                    recycler.postIfAlive(isAlive = { bindingAdapterPosition != RecyclerView.NO_POSITION }) {
                                        requestVideoFocus(position = pos - 1, attempt = 0)
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    // Let focus move to the previous section when possible; if no candidate exists,
                                    // explicitly route focus to the Activity tab bar to avoid "focus sink" behavior.
                                    if (view.focusSearch(View.FOCUS_UP) != null) return@setOnKeyListener false
                                    onRequestMoveToTabs?.invoke() == true
                                }

                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    // Let focus move to the next section when possible; only consume when
                                    // no candidate exists to avoid escaping to global/system UI.
                                    view.focusSearch(View.FOCUS_DOWN) == null
                                }

                                else -> false
                            }
                        }
                    }

                    override fun onChildViewDetachedFromWindow(view: View) {
                        view.setOnKeyListener(null)
                    }
                },
            )
        }

        private fun requestVideoFocus(position: Int, attempt: Int) {
            val recycler = binding.recyclerVideos
            val isUiAlive = { bindingAdapterPosition != RecyclerView.NO_POSITION && recycler.isAttachedToWindow }
            recycler.postIfAlive(isAlive = isUiAlive) {
                val v = recycler.findViewHolderForAdapterPosition(position)?.itemView
                if (v?.requestFocus() == true) return@postIfAlive

                if (attempt >= 30) return@postIfAlive
                recycler.scrollToPosition(position)
                recycler.postDelayedIfAlive(delayMillis = 16, isAlive = isUiAlive) { requestVideoFocus(position = position, attempt = attempt + 1) }
            }
        }
    }
}
