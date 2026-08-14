package blbl.cat3399.feature.feed

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.video.VideoPlayKind
import blbl.cat3399.core.api.video.VideoPlayRequest
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.databinding.FragmentVideoFeedBinding
import blbl.cat3399.feature.player.ArchiveTripleActionState
import blbl.cat3399.feature.player.Playable
import blbl.cat3399.feature.player.PlayerActivity
import blbl.cat3399.feature.player.engine.ExoPlayerEngine
import blbl.cat3399.feature.player.engine.PlaybackSource
import blbl.cat3399.feature.player.executeArchiveTripleAction
import blbl.cat3399.feature.player.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immersive, TikTok-style vertical feed. A single shared [ExoPlayerEngine] renders the focused
 * page; paging up/down swaps which item's [SurfaceView] the engine draws into and loads the next
 * video. Pressing OK opens the full [PlayerActivity] (danmaku, comments, speed, etc.).
 */
class VideoFeedFragment : Fragment() {

    private var _binding: FragmentVideoFeedBinding? = null
    private val binding get() = _binding!!

    private val adapter = FeedAdapter(
        onDislike = { onDislike(it) },
        onDetail = { openPlayerAt(it) },
    )

    private var engine: ExoPlayerEngine? = null
    private lateinit var layoutManager: LinearLayoutManager
    private var snapHelper: PagerSnapHelper? = null

    private var freshIdx = 1
    private var loading = false
    private var finished = false
    private var firstLoadDone = false
    private var desiredPosition = 0
    private var currentPlayingBvid: String? = null
    private var loadJob: Job? = null

    // 长按 OK 5 秒一键三连
    private var tripleOverlay: TripleActionOverlay? = null
    private val holdHandler = Handler(Looper.getMainLooper())
    private val tripleTrigger = Runnable { doTriple() }
    private var holdingTriple = false
    private var tripleFired = false
    private val TRIPLE_HOLD_MS = 5000L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVideoFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        engine = ExoPlayerEngine(requireContext())

        layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.feedRecycler.layoutManager = layoutManager
        binding.feedRecycler.adapter = adapter
        snapHelper = PagerSnapHelper().also { it.attachToRecyclerView(binding.feedRecycler) }

        binding.feedRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos = findCenterPosition()
                    if (pos >= 0) playAt(pos)
                }
            }
        })

        binding.root.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    pageTo(desiredPosition + 1)
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    pageTo(desiredPosition - 1)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    focusActions()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    binding.root.requestFocus()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> handleCenterKey(event)
                else -> false
            }
        }

        tripleOverlay = TripleActionOverlay(binding.feedTripleOverlay)

        // The feed captures D-pad on its root, so it must hold focus to receive keypresses.
        binding.root.requestFocus()
        loadMore()
    }

    private fun findCenterPosition(): Int {
        val helper = snapHelper ?: return RecyclerView.NO_POSITION
        val view = helper.findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
        return layoutManager.getPosition(view)
    }

    private fun pageTo(pos: Int) {
        val target = pos.coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0))
        desiredPosition = target
        binding.feedRecycler.smoothScrollToPosition(target)
    }

    private fun focusActions() {
        val holder = binding.feedRecycler.findViewHolderForAdapterPosition(desiredPosition) as? FeedAdapter.FeedCardViewHolder
        holder?.binding?.feedBtnDislike?.requestFocus()
    }

    /**
     * OK key on the feed does double duty:
     * - short press (release before [TRIPLE_HOLD_MS]) → open the full player;
     * - long press (held [TRIPLE_HOLD_MS]) → one-click triple (like + coin + fav) on the centered video.
     */
    private fun handleCenterKey(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (holdingTriple || tripleFired || tripleOverlay?.isPlaying == true) return true
                holdingTriple = true
                tripleOverlay?.startHoldProgress()
                holdHandler.removeCallbacks(tripleTrigger)
                holdHandler.postDelayed(tripleTrigger, TRIPLE_HOLD_MS)
                true
            }
            KeyEvent.ACTION_UP -> {
                if (tripleFired) {
                    tripleFired = false
                    return true
                }
                if (holdingTriple) {
                    holdingTriple = false
                    holdHandler.removeCallbacks(tripleTrigger)
                    tripleOverlay?.cancelHoldProgress()
                    openPlayerAt(desiredPosition)
                }
                true
            }
            else -> true
        }
    }

    private fun doTriple() {
        holdingTriple = false
        tripleFired = true
        val ov = tripleOverlay ?: return
        ov.endHoldForSuccess()

        if (!BiliClient.cookies.hasSessData()) {
            ov.cancelHoldProgress()
            AppToast.show(requireContext(), "请先登录后再一键三连")
            return
        }
        val card = adapter.items.getOrNull(desiredPosition) ?: run {
            ov.cancelHoldProgress()
            AppToast.show(requireContext(), "未获取到视频信息")
            return
        }
        val selfMid = BiliClient.cookies.getCookieValue("DedeUserID")
            ?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = executeArchiveTripleAction(
                    bvid = card.bvid,
                    aid = card.aid,
                    selfMid = selfMid,
                    initialState = ArchiveTripleActionState(liked = false, coinCount = 0, favored = false),
                    isStillValid = { !isDetached && !isRemoving },
                )
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    ov.playSuccess()
                    AppToast.show(requireContext(), result.toastMessage())
                }
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    AppToast.show(requireContext(), t.userMessage(defaultMessage = "三连失败"))
                }
            }
        }
    }

    private fun openPlayerAt(pos: Int) {
        val card = adapter.items.getOrNull(pos) ?: return
        openPlayer(card)
    }

    private fun openPlayer(card: VideoCard) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("bvid", card.bvid)
            card.cid?.let { putExtra("cid", it) }
            card.aid?.let { putExtra("aid", it) }
        }
        startActivity(intent)
    }

    private fun onDislike(pos: Int) {
        val card = adapter.items.getOrNull(pos) ?: return
        val index = pos.coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0))
        adapter.removeAt(index)
        // Pull one more so the feed never runs dry after a removal.
        loadMore()
        desiredPosition = index.coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0))
        playAt(desiredPosition)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { BiliApi.videoFeedbackDislike(card) }
        }
    }

    private fun playAt(position: Int) {
        desiredPosition = position
        val holder = binding.feedRecycler.findViewHolderForAdapterPosition(position) as? FeedAdapter.FeedCardViewHolder
        if (holder == null) {
            binding.feedRecycler.post { if (desiredPosition == position && _binding != null) playAt(position) }
            return
        }
        holder.onSurfaceChanged = { surf ->
            if (desiredPosition == position && _binding != null) {
                val s = surf ?: holder.surface
                if (s != null && s.isValid) startPlay(position, s, holder)
            }
        }
        val s = holder.surface
        if (s != null && s.isValid) startPlay(position, s, holder)
    }

    private fun startPlay(position: Int, surface: Surface, holder: FeedAdapter.FeedCardViewHolder) {
        val card = adapter.items.getOrNull(position) ?: return
        val eng = engine ?: return
        // ExoPlayerEngine.setVideoSurface() is a no-op (it renders through a PlayerView inside
        // PlayerActivity). For the immersive feed we own a SurfaceView, so attach the surface
        // directly on the underlying ExoPlayer instance.
        eng.exoPlayer.setVideoSurface(surface)
        if (currentPlayingBvid == card.bvid && eng.isPlaying) {
            holder.binding.feedCover.visibility = View.GONE
            return
        }
        currentPlayingBvid = card.bvid
        eng.stop()
        holder.binding.feedCover.visibility = View.VISIBLE
        holder.binding.feedProgress.visibility = View.VISIBLE
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val playable = withContext(Dispatchers.IO) { resolveFeedPlayable(card) }
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                if (desiredPosition != position) return@withContext
                if (playable == null) {
                    holder.binding.feedProgress.visibility = View.GONE
                    return@withContext
                }
                eng.setSource(PlaybackSource.Vod(playable, null))
                eng.prepare()
                eng.play()
                currentPlayingBvid = card.bvid
                holder.binding.feedProgress.visibility = View.GONE
                holder.binding.feedCover.visibility = View.GONE
            }
        }
    }

    private suspend fun resolveFeedPlayable(card: VideoCard): Playable? =
        runCatching {
            val stream = BiliApi.playUrl(
                VideoPlayRequest(
                    kind = VideoPlayKind.UGC,
                    bvid = card.bvid,
                    cid = card.cid,
                    aid = card.aid,
                    qn = 64,
                    fnval = 16,
                )
            )
            stream.toFeedPlayable()
        }.getOrNull()

    private fun loadMore() {
        if (loading || finished) return
        loading = true
        if (!firstLoadDone) binding.feedLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    BiliApi.recommend(freshIdx = freshIdx, ps = 20, fetchRow = 1)
                }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (list.isEmpty()) {
                        finished = true
                        if (adapter.itemCount == 0) {
                            showStatus(getString(R.string.feed_load_failed))
                        } else {
                            binding.feedLoading.visibility = View.GONE
                        }
                    } else {
                        freshIdx++
                        adapter.append(list)
                        if (!firstLoadDone) {
                            firstLoadDone = true
                            binding.feedLoading.visibility = View.GONE
                            playAt(0)
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (adapter.itemCount == 0) {
                        showStatus(getString(R.string.feed_load_failed))
                    } else {
                        binding.root.postDelayed({ if (!finished && _binding != null) loadMore() }, 3000)
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun showStatus(text: String) {
        binding.feedLoading.visibility = View.GONE
        binding.feedStatus.visibility = View.VISIBLE
        binding.feedStatus.text = text
    }

    override fun onPause() {
        engine?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val holder = binding.feedRecycler.findViewHolderForAdapterPosition(desiredPosition) as? FeedAdapter.FeedCardViewHolder
        if (holder != null && currentPlayingBvid != null && _binding != null) {
            engine?.play()
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        holdHandler.removeCallbacksAndMessages(null)
        tripleOverlay?.cancelAll()
        engine?.release()
        engine = null
        snapHelper = null
        _binding = null
        super.onDestroyView()
    }
}
