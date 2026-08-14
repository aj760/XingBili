package blbl.cat3399.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveApiParseTest {
    private class MapJsonObj(
        private val values: Map<String, Any?>,
    ) : LiveApi.JsonObj {
        override fun optString(name: String, fallback: String): String {
            val v = values[name] ?: return fallback
            return when (v) {
                is String -> v
                is Number -> v.toString()
                is Boolean -> v.toString()
                else -> v.toString()
            }
        }

        override fun optLong(name: String): Long = optLong(name, 0L)

        override fun optLong(name: String, fallback: Long): Long {
            val v = values[name] ?: return fallback
            return when (v) {
                is Number -> v.toLong()
                is String -> v.trim().toLongOrNull() ?: fallback
                is Boolean -> if (v) 1L else 0L
                else -> fallback
            }
        }

        override fun optInt(name: String, fallback: Int): Int {
            val v = values[name] ?: return fallback
            return when (v) {
                is Number -> v.toInt()
                is String -> v.trim().toIntOrNull() ?: fallback
                is Boolean -> if (v) 1 else 0
                else -> fallback
            }
        }
    }

    @Test
    fun parseCnCount_should_handle_cn_units() {
        assertEquals(0L, LiveApi.parseCnCount(""))
        assertEquals(0L, LiveApi.parseCnCount("abc"))
        assertEquals(123L, LiveApi.parseCnCount("123"))
        assertEquals(12_000L, LiveApi.parseCnCount("1.2万"))
        assertEquals(300_000_000L, LiveApi.parseCnCount("3亿"))
    }

    @Test
    fun rewriteLiveOriginM3u8Path_should_strip_transcode_suffix_for_index_m3u8() {
        assertEquals(
            "/live-bvc/682659/live_103128201_64134939/index.m3u8",
            LiveApi.rewriteLiveOriginM3u8Path("/live-bvc/682659/live_103128201_64134939_2500/index.m3u8"),
        )
    }

    @Test
    fun rewriteLiveOriginM3u8Path_should_strip_transcode_suffix_for_m3u8_file() {
        assertEquals(
            "/live-bvc/251987/live_1805680_3999664.m3u8",
            LiveApi.rewriteLiveOriginM3u8Path("/live-bvc/251987/live_1805680_3999664_minihevc.m3u8"),
        )
    }

    @Test
    fun tryBuildLiveOriginHlsUrl_should_drop_query_and_keep_signed_fallback_for_non_hls() {
        val host = "https://d1--cn-gotcha204b.bilivideo.com"
        assertEquals(
            "https://d1--cn-gotcha204b.bilivideo.com/live-bvc/682659/live_103128201_64134939/index.m3u8",
            LiveApi.tryBuildLiveOriginHlsUrl(
                host = host,
                baseUrl = "/live-bvc/682659/live_103128201_64134939_2500/index.m3u8?",
            ),
        )
        assertEquals(
            null,
            LiveApi.tryBuildLiveOriginHlsUrl(
                host = host,
                baseUrl = "/live-bvc/682659/live_103128201_64134939_2500.flv?",
            ),
        )
    }

    @Test
    fun parseLiveFollowingRoom_should_parse_basic_fields() {
        val card =
            LiveApi.parseLiveFollowingRoom(
                MapJsonObj(
                    mapOf(
                        "roomid" to 123L,
                        "uid" to 456L,
                        "title" to "hello",
                        "uname" to "up",
                        "room_cover" to "https://i.example.com/room.jpg",
                        "face" to "https://i.example.com/face.jpg",
                        "text_small" to "1.2万",
                        "live_status" to 1,
                        "parent_area_id" to 9,
                        "area_v2_parent_name" to "parent",
                        "area_id" to 10,
                        "area_name_v2" to "area",
                    ),
                ),
            )

        assertNotNull(card)
        val c = card!!
        assertEquals(123L, c.roomId)
        assertEquals(456L, c.uid)
        assertEquals("hello", c.title)
        assertEquals("up", c.uname)
        assertEquals("https://i.example.com/room.jpg", c.coverUrl)
        assertEquals("https://i.example.com/face.jpg", c.faceUrl)
        assertEquals(12_000L, c.online)
        assertEquals(true, c.isLive)
        assertEquals(9, c.parentAreaId)
        assertEquals("parent", c.parentAreaName)
        assertEquals(10, c.areaId)
        assertEquals("area", c.areaName)
    }

    @Test
    fun parseLiveFollowingRoom_should_skip_invalid_roomid() {
        val card =
            LiveApi.parseLiveFollowingRoom(
                MapJsonObj(
                    mapOf(
                        "roomid" to 0L,
                        "title" to "ignored",
                    ),
                ),
            )
        assertEquals(null, card)
    }
}
