package blbl.cat3399.feature.my

import androidx.fragment.app.Fragment

interface MyNavigator {
    fun openFavFolder(mediaId: Long, title: String)

    fun openBangumiDetail(
        seasonId: Long,
        isDrama: Boolean,
        continueEpId: Long? = null,
        continueEpIndex: Int? = null,
    )
}

fun Fragment.findMyNavigator(): MyNavigator? {
    return generateSequence(parentFragment) { it.parentFragment }
        .filterIsInstance<MyNavigator>()
        .firstOrNull()
        ?: (activity as? MyNavigator)
}
