package com.mytvb.feature.player.view

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放器按键路由纯函数的单元测试。
 *
 * dispatchKeyEvent 拆分后，seek 键判定收敛到 [MyPlayerView.isSeekKeyCode] /
 * [MyPlayerView.isForwardSeekKeyCode]（此前同一判定在巨型方法里重复 5 处）。
 * 键位语义（含部分电视 ROM 的系统导航兼容键码 282/283）在此处锁定，防止拆分/
 * 改键时回归。
 */
class MyPlayerViewKeyRoutingTest {

    // ---- isSeekKeyCode：左右方向键及系统导航兼容键 ----

    @Test
    fun dpadLeftAndRightAreSeekKeys() {
        assertTrue(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun systemNavigationCompatLeftRightAreSeekKeys() {
        // 部分电视 ROM 把左右方向键报成 280-283 系统导航键码（见 companion 常量定义）
        assertTrue(MyPlayerView.isSeekKeyCode(282))
        assertTrue(MyPlayerView.isSeekKeyCode(283))
    }

    @Test
    fun verticalAndOkKeysAreNotSeekKeys() {
        assertFalse(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_ENTER))
        assertFalse(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_BACK))
        assertFalse(MyPlayerView.isSeekKeyCode(KeyEvent.KEYCODE_MENU))
    }

    // ---- isForwardSeekKeyCode：前进方向 ----

    @Test
    fun rightAndCompatRightAreForward() {
        assertTrue(MyPlayerView.isForwardSeekKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(MyPlayerView.isForwardSeekKeyCode(283))
    }

    @Test
    fun leftAndCompatLeftAreNotForward() {
        assertFalse(MyPlayerView.isForwardSeekKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(MyPlayerView.isForwardSeekKeyCode(282))
    }
}
