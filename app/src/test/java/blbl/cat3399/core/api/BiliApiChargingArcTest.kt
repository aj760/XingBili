package blbl.cat3399.core.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliApiChargingArcTest {
    @Test
    fun isChargingArc_should_respect_arc_fields() {
        assertTrue(
            BiliApi.isChargingArc(
                isChargingArc = true,
                elecArcType = 0,
                badgeText = null,
            ),
        )
        assertTrue(
            BiliApi.isChargingArc(
                isChargingArc = false,
                elecArcType = 1,
                badgeText = null,
            ),
        )
    }

    @Test
    fun isChargingArc_should_accept_dynamic_badge_text() {
        assertTrue(
            BiliApi.isChargingArc(
                isChargingArc = false,
                elecArcType = 0,
                badgeText = "充电专属",
            ),
        )
        assertTrue(
            BiliApi.isChargingArc(
                isChargingArc = false,
                elecArcType = 0,
                badgeText = "  充电专属  ",
            ),
        )
    }

    @Test
    fun isChargingArc_should_be_false_for_regular_badges() {
        assertFalse(
            BiliApi.isChargingArc(
                isChargingArc = false,
                elecArcType = 0,
                badgeText = "投稿视频",
            ),
        )
        assertFalse(
            BiliApi.isChargingArc(
                isChargingArc = false,
                elecArcType = 0,
                badgeText = "动态视频",
            ),
        )
        assertFalse(
            BiliApi.isChargingArc(
                isChargingArc = false,
                elecArcType = 0,
                badgeText = null,
            ),
        )
    }
}

