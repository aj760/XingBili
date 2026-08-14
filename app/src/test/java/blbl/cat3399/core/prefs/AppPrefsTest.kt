package blbl.cat3399.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPrefsTest {
    @Test
    fun normalizePlayerAutoSkipServerBaseUrl_should_trim_and_remove_trailing_slash() {
        val normalized = AppPrefs.normalizePlayerAutoSkipServerBaseUrl("  https://bsbsb.top/  ")

        assertEquals("https://bsbsb.top", normalized)
    }

    @Test
    fun normalizePlayerAutoSkipServerBaseUrl_should_keep_http_ip_address() {
        val normalized = AppPrefs.normalizePlayerAutoSkipServerBaseUrl("http://154.222.28.109/")

        assertEquals("http://154.222.28.109", normalized)
    }

    @Test
    fun normalizePlayerAutoSkipServerBaseUrl_should_reject_invalid_values() {
        assertNull(AppPrefs.normalizePlayerAutoSkipServerBaseUrl(""))
        assertNull(AppPrefs.normalizePlayerAutoSkipServerBaseUrl("bsbsb.top"))
        assertNull(AppPrefs.normalizePlayerAutoSkipServerBaseUrl("https://bsbsb.top/api?x=1"))
    }

    @Test
    fun normalizeLegacyDanmakuAreaCompat_should_map_legacy_fraction_steps_to_percent_steps() {
        assertEquals(0.2f, AppPrefs.normalizeLegacyDanmakuAreaCompat(1f / 6f), 0.0001f)
        assertEquals(0.3f, AppPrefs.normalizeLegacyDanmakuAreaCompat(0.25f), 0.0001f)
        assertEquals(0.7f, AppPrefs.normalizeLegacyDanmakuAreaCompat(2f / 3f), 0.0001f)
        assertEquals(0.8f, AppPrefs.normalizeLegacyDanmakuAreaCompat(0.75f), 0.0001f)
        assertEquals(1.0f, AppPrefs.normalizeLegacyDanmakuAreaCompat(1.0f), 0.0001f)
    }

    @Test
    fun normalizeDanmakuArea_should_snap_to_nearest_ten_percent_step() {
        assertEquals(0.1f, AppPrefs.normalizeDanmakuArea(0.09f), 0.0001f)
        assertEquals(0.3f, AppPrefs.normalizeDanmakuArea(0.26f), 0.0001f)
        assertEquals(0.6f, AppPrefs.normalizeDanmakuArea(0.55f), 0.0001f)
        assertEquals(1.0f, AppPrefs.normalizeDanmakuArea(Float.NaN), 0.0001f)
    }
}
