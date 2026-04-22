package com.webdav.music.data

import com.webdav.music.data.model.PlayerState
import com.webdav.music.data.model.RepeatMode
import org.junit.Assert.*
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `default PlayerState has correct initial values`() {
        val state = PlayerState()

        assertFalse(state.isPlaying)
        assertNull(state.currentMusic)
        assertTrue(state.playlist.isEmpty())
        assertEquals(0, state.currentIndex)
        assertEquals(0L, state.progress)
        assertEquals(0L, state.duration)
        assertFalse(state.shuffleMode)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }

    @Test
    fun `copy updates values correctly`() {
        val state = PlayerState()
        val updated = state.copy(
            isPlaying = true,
            progress = 5000L,
            duration = 30000L
        )

        assertTrue(updated.isPlaying)
        assertEquals(5000L, updated.progress)
        assertEquals(30000L, updated.duration)
        assertFalse(updated.shuffleMode)
        assertEquals(RepeatMode.OFF, updated.repeatMode)
    }
}

class RepeatModeTest {

    @Test
    fun `repeat mode cycles correctly OFF to ALL to ONE to OFF`() {
        var mode = RepeatMode.OFF

        // OFF -> ALL
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ALL, mode)

        // ALL -> ONE
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ONE, mode)

        // ONE -> OFF
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.OFF, mode)
    }
}
