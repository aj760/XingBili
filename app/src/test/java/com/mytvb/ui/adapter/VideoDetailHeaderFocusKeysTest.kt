package com.mytvb.ui.adapter

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDetailHeaderFocusKeysTest {

    @Test
    fun shouldFocusBack_onlyOnLeftKeyDown() {
        assertTrue(
            VideoDetailHeaderFocusKeys.shouldFocusBack(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.ACTION_DOWN
            )
        )
        assertFalse(
            VideoDetailHeaderFocusKeys.shouldFocusBack(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.ACTION_UP
            )
        )
        assertFalse(
            VideoDetailHeaderFocusKeys.shouldFocusBack(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.ACTION_DOWN
            )
        )
    }
}
