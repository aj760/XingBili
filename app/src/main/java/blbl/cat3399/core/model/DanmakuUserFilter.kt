package blbl.cat3399.core.model

data class DanmakuUserFilter(
    val keywords: List<String> = emptyList(),
    val regexes: List<Regex> = emptyList(),
    val blockedUserMidHashes: Set<String> = emptySet(),
) {
    fun isEmpty(): Boolean = keywords.isEmpty() && regexes.isEmpty() && blockedUserMidHashes.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    companion object {
        val EMPTY = DanmakuUserFilter()
    }
}

