package blbl.cat3399.feature.search

import blbl.cat3399.R

enum class VideoOrder(
    val apiValue: String,
    val labelRes: Int,
) {
    TotalRank("totalrank", R.string.search_sort_totalrank),
    Click("click", R.string.search_sort_click),
    PubDate("pubdate", R.string.search_sort_pubdate),
    Dm("dm", R.string.search_sort_dm),
    Stow("stow", R.string.search_sort_stow),
    Scores("scores", R.string.search_sort_scores),
}

enum class LiveOrder(
    val apiValue: String,
    val labelRes: Int,
) {
    Online("online", R.string.search_sort_live_online),
    LiveTime("live_time", R.string.search_sort_live_time),
}

enum class UserOrder(
    val apiValue: String,
    val labelRes: Int,
) {
    Default("0", R.string.search_sort_user_default),
    Fans("fans", R.string.search_sort_user_fans),
    Level("level", R.string.search_sort_user_level),
}

enum class SearchTab(
    val index: Int,
) {
    Video(0),
    Bangumi(1),
    Media(2),
    Live(3),
    User(4),
    ;

    companion object {
        fun forIndex(index: Int): SearchTab {
            return entries.firstOrNull { it.index == index } ?: Video
        }
    }
}
