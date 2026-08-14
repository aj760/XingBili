package blbl.cat3399.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuShieldTest {
    @Test
    fun allow_should_block_by_keyword() {
        val shield = DanmakuShield(keywords = listOf("foo"))
        val hit =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "hello foo world",
                color = 0,
                fontSize = 25,
                weight = 0,
            )
        val miss =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "hello bar world",
                color = 0,
                fontSize = 25,
                weight = 0,
            )
        assertFalse(shield.allow(hit))
        assertTrue(shield.allow(miss))
    }

    @Test
    fun allow_should_block_by_regex() {
        val shield = DanmakuShield(regexes = listOf(Regex("^foo\\d+$")))
        val hit =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "foo123",
                color = 0,
                fontSize = 25,
                weight = 0,
            )
        val miss =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "bar",
                color = 0,
                fontSize = 25,
                weight = 0,
            )
        assertFalse(shield.allow(hit))
        assertTrue(shield.allow(miss))
    }

    @Test
    fun allow_should_block_by_user_midHash() {
        val shield = DanmakuShield(blockedUserMidHashes = setOf("59417e95"))
        val hitLower =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "hello",
                color = 0,
                fontSize = 25,
                weight = 0,
                midHash = "59417e95",
            )
        val hitUpper =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "hello",
                color = 0,
                fontSize = 25,
                weight = 0,
                midHash = "59417E95",
            )
        val noHash =
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = "hello",
                color = 0,
                fontSize = 25,
                weight = 0,
                midHash = null,
            )
        assertFalse(shield.allow(hitLower))
        assertFalse(shield.allow(hitUpper))
        assertTrue(shield.allow(noHash))
    }
}

