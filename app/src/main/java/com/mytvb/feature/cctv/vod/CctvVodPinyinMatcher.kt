package com.mytvb.feature.cctv.vod

import android.os.Build
import android.icu.text.Transliterator

/**
 * 片库搜索的拼音匹配工具。
 *
 * 自绘键盘只能输字母，而片库剧名是中文：把标题转成拼音（全拼小写）后做
 * contains 匹配，「zhengtu」即可命中《征途》。转写用系统 ICU（API 24+），
 * 低版本直接返回 null（只能按标题原文匹配）。结果按标题缓存，转换一次反复用。
 */
object CctvVodPinyinMatcher {

    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
            }.getOrNull()
        } else {
            null
        }
    }

    private val cache = HashMap<String, String>()

    /** 标题的拼音全拼（小写，仅保留字母数字）；不支持时返回 null。 */
    fun pinyinOf(title: String): String? {
        if (transliterator == null) return null
        synchronized(cache) {
            cache[title]?.let { return it }
        }
        val cleaned = title.replace(Regex("[《》\\s]"), "")
        val pinyin = runCatching {
            transliterator!!.transliterate(cleaned).replace(Regex("[^a-z0-9]"), "")
        }.getOrElse { return null }
        synchronized(cache) { cache[title] = pinyin }
        return pinyin
    }
}
