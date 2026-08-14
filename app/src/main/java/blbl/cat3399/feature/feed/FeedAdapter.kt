package blbl.cat3399.feature.feed

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.databinding.FeedCardItemBinding

/**
 * Adapter backing the immersive vertical feed. Each item owns a [SurfaceView] whose [Surface] is
 * handed to the single shared player engine when that item becomes the active page. Cover image,
 * title and meta are bound here; playback lifecycle is driven by [VideoFeedFragment].
 */
class FeedAdapter(
    private val onDislike: (Int) -> Unit,
    private val onDetail: (Int) -> Unit,
) : RecyclerView.Adapter<FeedAdapter.FeedCardViewHolder>() {

    val items = ArrayList<VideoCard>()

    fun append(list: List<VideoCard>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun removeAt(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedCardViewHolder {
        val binding = FeedCardItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeedCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedCardViewHolder, position: Int) {
        val card = items.getOrNull(position) ?: return
        holder.bind(card, onDislike, onDetail)
    }

    class FeedCardViewHolder(val binding: FeedCardItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        var surface: Surface? = null
            private set
        var onSurfaceChanged: ((Surface?) -> Unit)? = null

        init {
            binding.feedSurface.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    surface = holder.surface
                    onSurfaceChanged?.invoke(surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    surface = holder.surface
                    onSurfaceChanged?.invoke(surface)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    surface = null
                    onSurfaceChanged?.invoke(null)
                }
            })
        }

        fun bind(
            card: VideoCard,
            onDislike: (Int) -> Unit,
            onDetail: (Int) -> Unit,
        ) {
            binding.feedTitle.text = card.title
            binding.feedUp.text = card.ownerName
            binding.feedMeta.text = buildMeta(card)
            binding.feedCover.visibility = View.VISIBLE
            binding.feedProgress.visibility = View.GONE
            ImageLoader.loadInto(binding.feedCover, card.coverUrl)

            val posProvider = { bindingAdapterPosition }
            binding.feedBtnDislike.setOnClickListener { onDislike(posProvider()) }
            binding.feedBtnDetail.setOnClickListener { onDetail(posProvider()) }

            binding.feedBtnDislike.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        onDislike(posProvider())
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        binding.root.requestFocus()
                        true
                    }
                    else -> false
                }
            }
            binding.feedBtnDetail.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        onDetail(posProvider())
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        binding.root.requestFocus()
                        true
                    }
                    else -> false
                }
            }
        }

        private fun buildMeta(card: VideoCard): String {
            val parts = ArrayList<String>()
            card.view?.let { parts.add(formatCount(it)) }
            if (card.durationSec > 0) parts.add(formatDuration(card.durationSec))
            return parts.joinToString("  ")
        }

        private fun formatCount(n: Long): String =
            when {
                n >= 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
                n >= 10_000 -> String.format("%.1f万", n / 10_000.0)
                else -> n.toString()
            }

        private fun formatDuration(sec: Int): String {
            val m = sec / 60
            val s = sec % 60
            return if (m >= 60) {
                String.format("%d:%02d:%02d", m / 60, m % 60, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }
    }
}
