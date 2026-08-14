package blbl.cat3399.feature.category

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.Zone
import blbl.cat3399.feature.video.VideoGridFragment
import android.os.SystemClock

class CategoryPagerAdapter(
    fragment: Fragment,
    private val zones: List<Zone>,
) : FragmentStateAdapter(fragment) {
    // Deterministic, collision-free stable IDs. Each zone has a unique positive `rid`; the
    // "all" zone (rid == null) uses a negative sentinel. Using the raw hash code previously
    // caused two distinct zones to share an itemId, which made ViewPager2 associate a tab with
    // the wrong page fragment (state mix-ups / wrong content shown).
    private companion object {
        const val ALL_ZONE_ID: Long = -1L
    }

    private fun stableIdFor(zone: Zone): Long = zone.rid?.toLong() ?: ALL_ZONE_ID

    override fun getItemCount(): Int = zones.size

    override fun createFragment(position: Int): Fragment {
        val zone = zones[position]
        AppLog.d(
            "Category",
            "createFragment pos=$position title=${zone.title} rid=${zone.rid} t=${SystemClock.uptimeMillis()}",
        )
        return if (zone.rid == null) {
            VideoGridFragment.newPopular()
        } else {
            VideoGridFragment.newRegion(zone.rid)
        }
    }

    override fun getItemId(position: Int): Long = stableIdFor(zones[position])

    override fun containsItem(itemId: Long): Boolean = zones.any { stableIdFor(it) == itemId }
}
