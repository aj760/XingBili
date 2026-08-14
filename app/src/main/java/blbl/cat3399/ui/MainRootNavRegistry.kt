package blbl.cat3399.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import blbl.cat3399.R
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.feature.category.CategoryFragment
import blbl.cat3399.feature.custom.CustomPageFragment
import blbl.cat3399.feature.custom.CustomPageTabRegistry
import blbl.cat3399.feature.dynamic.DynamicFragment
import blbl.cat3399.feature.home.HomeFragment
import blbl.cat3399.feature.live.LiveFragment
import blbl.cat3399.feature.my.MyFragment
import blbl.cat3399.feature.search.SearchFragment

data class RootNavSpec(
    val navId: Int,
    val key: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val startupPageKey: String? = null,
    val isEnabled: () -> Boolean = { true },
    val matchesFragment: (Fragment) -> Boolean,
    val fragmentFactory: () -> Fragment,
)

object MainRootNavRegistry {
    private const val ROOT_TAG_PREFIX = "main_root_"

    private val specs: List<RootNavSpec> =
        listOf(
            RootNavSpec(
                navId = SidebarNavAdapter.ID_SEARCH,
                key = "search",
                titleRes = R.string.tab_search,
                iconRes = R.drawable.ic_nav_search,
                matchesFragment = { it is SearchFragment },
                fragmentFactory = { SearchFragment.newInstance() },
            ),
            RootNavSpec(
                navId = SidebarNavAdapter.ID_HOME,
                key = "home",
                titleRes = R.string.tab_recommend,
                iconRes = R.drawable.ic_nav_home,
                startupPageKey = AppPrefs.STARTUP_PAGE_HOME,
                matchesFragment = { it is HomeFragment },
                fragmentFactory = { HomeFragment.newInstance() },
            ),
            RootNavSpec(
                navId = SidebarNavAdapter.ID_CATEGORY,
                key = "category",
                titleRes = R.string.tab_category,
                iconRes = R.drawable.ic_nav_category,
                startupPageKey = AppPrefs.STARTUP_PAGE_CATEGORY,
                matchesFragment = { it is CategoryFragment },
                fragmentFactory = { CategoryFragment.newInstance() },
            ),
            RootNavSpec(
                navId = SidebarNavAdapter.ID_DYNAMIC,
                key = "dynamic",
                titleRes = R.string.tab_dynamic,
                iconRes = R.drawable.ic_nav_dynamic,
                startupPageKey = AppPrefs.STARTUP_PAGE_DYNAMIC,
                matchesFragment = { it is DynamicFragment },
                fragmentFactory = { DynamicFragment.newInstance() },
            ),
            RootNavSpec(
                navId = SidebarNavAdapter.ID_LIVE,
                key = "live",
                titleRes = R.string.tab_live,
                iconRes = R.drawable.ic_nav_live,
                startupPageKey = AppPrefs.STARTUP_PAGE_LIVE,
                matchesFragment = { it is LiveFragment },
                fragmentFactory = { LiveFragment.newInstance() },
            ),
            RootNavSpec(
                navId = SidebarNavAdapter.ID_CUSTOM,
                key = "custom",
                titleRes = R.string.tab_custom,
                iconRes = R.drawable.ic_nav_custom,
                startupPageKey = AppPrefs.STARTUP_PAGE_CUSTOM,
                isEnabled = {
                    CustomPageTabRegistry.isEnabled(
                        config = BiliClient.prefs.customPageConfig,
                        isLoggedIn = BiliClient.cookies.hasSessData(),
                    )
                },
                matchesFragment = { it is CustomPageFragment },
                fragmentFactory = { CustomPageFragment.newInstance() },
            ),
            RootNavSpec(
                navId = SidebarNavAdapter.ID_MY,
                key = "my",
                titleRes = R.string.tab_my,
                iconRes = R.drawable.ic_nav_my,
                startupPageKey = AppPrefs.STARTUP_PAGE_MY,
                matchesFragment = { it is MyFragment },
                fragmentFactory = { MyFragment.newInstance() },
            ),
        )

    fun enabledSpecs(): List<RootNavSpec> = specs.filter { it.isEnabled() }

    fun startupSpecs(): List<RootNavSpec> = specs.filter { it.startupPageKey != null }

    fun enabledStartupSpecs(): List<RootNavSpec> = enabledSpecs().filter { it.startupPageKey != null }

    fun sidebarItems(context: Context): List<SidebarNavAdapter.NavItem> {
        return enabledSpecs().map { spec ->
            SidebarNavAdapter.NavItem(
                id = spec.navId,
                title = context.getString(spec.titleRes),
                iconRes = spec.iconRes,
            )
        }
    }

    fun resolveLaunchNavId(startupPageKey: String): Int {
        return enabledStartupSpecs().firstOrNull { it.startupPageKey == startupPageKey }?.navId
            ?: SidebarNavAdapter.ID_HOME
    }

    fun startupTitle(context: Context, startupPageKey: String): String {
        return startupSpecs().firstOrNull { it.startupPageKey == startupPageKey }
            ?.let { context.getString(it.titleRes) }
            ?: context.getString(R.string.tab_recommend)
    }

    fun specForNavId(navId: Int): RootNavSpec? = specs.firstOrNull { it.navId == navId }

    fun enabledSpecForNavId(navId: Int): RootNavSpec? = specForNavId(navId)?.takeIf { it.isEnabled() }

    fun rootNavIds(): IntArray = enabledSpecs().map { it.navId }.toIntArray()

    fun rootTagFor(navId: Int): String {
        val spec = specForNavId(navId) ?: throw IllegalArgumentException("Unknown root navId=$navId")
        return ROOT_TAG_PREFIX + spec.key
    }

    fun isRootTag(tag: String?): Boolean = tag?.startsWith(ROOT_TAG_PREFIX) == true

    fun navIdForFragment(fragment: Fragment?): Int? {
        if (fragment == null) return null
        val byTag = specs.firstOrNull { rootTagFor(it.navId) == fragment.tag }
        if (byTag != null) return byTag.navId
        return specs.firstOrNull { it.matchesFragment(fragment) }?.navId
    }
}
