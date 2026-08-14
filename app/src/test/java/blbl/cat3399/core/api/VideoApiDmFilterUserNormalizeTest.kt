package blbl.cat3399.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class VideoApiDmFilterUserNormalizeTest {
    @Test
    fun normalizeRegexRule_should_parse_slash_literal() {
        val r = VideoApi.normalizeRegexRule("/.*吐刀乐.*/")
        assertNotNull(r)
        val regex = r!!
        assertEquals(".*吐刀乐.*", regex.pattern)
        assertTrue(regex.containsMatchIn("xx吐刀乐yy"))
    }

    @Test
    fun normalizeRegexRule_should_support_flags() {
        val r = VideoApi.normalizeRegexRule("/foo/i")
        assertNotNull(r)
        val regex = r!!
        assertTrue(regex.containsMatchIn("FOO"))
    }

    @Test
    fun normalizeMidHashRule_should_pad_short_hashes() {
        assertEquals("00dc0589", VideoApi.normalizeMidHashRule("dc0589"))
        assertEquals("08b7a1dc", VideoApi.normalizeMidHashRule("8b7a1dc"))
        assertEquals("59417e95", VideoApi.normalizeMidHashRule("59417E95"))
    }

    @Test
    fun normalizeMidHashRule_should_keep_numeric_hashes() {
        assertEquals("31800768", VideoApi.normalizeMidHashRule("31800768"))
    }

    @Test
    fun normalizeMidHashRule_should_crc32_long_mid() {
        val mid = "3546586239994702"
        val crc = CRC32()
        crc.update(mid.toByteArray(Charsets.UTF_8))
        val expected = java.lang.Long.toHexString(crc.value).padStart(8, '0')
        assertEquals(expected, VideoApi.normalizeMidHashRule(mid))
    }
}

