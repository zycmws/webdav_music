package com.webdav.music.data

import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import org.junit.Assert.*
import org.junit.Test

class MusicItemTest {

    @Test
    fun `MusicItem stores all properties correctly`() {
        val item = MusicItem(
            id = "test_id",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 180000L,
            path = "/path/to/song.mp3",
            source = MusicSource.LOCAL,
            isDownloaded = false
        )

        assertEquals("test_id", item.id)
        assertEquals("Test Song", item.title)
        assertEquals("Test Artist", item.artist)
        assertEquals("Test Album", item.album)
        assertEquals(180000L, item.duration)
        assertEquals("/path/to/song.mp3", item.path)
        assertEquals(MusicSource.LOCAL, item.source)
        assertFalse(item.isDownloaded)
    }

    @Test
    fun `MusicItem default isDownloaded is false`() {
        val item = MusicItem(
            id = "test",
            title = "Title",
            artist = "Artist",
            album = "Album",
            duration = 0,
            path = "/path",
            source = MusicSource.LOCAL
        )
        assertFalse(item.isDownloaded)
    }

    @Test
    fun `MusicItem copy updates isDownloaded correctly`() {
        val item = MusicItem(
            id = "webdav_123",
            title = "Song",
            artist = "Artist",
            album = "Album",
            duration = 0,
            path = "https://example.com/song.mp3",
            source = MusicSource.WEBDAV,
            isDownloaded = false
        )

        val downloaded = item.copy(path = "/cache/song.mp3", isDownloaded = true)
        assertEquals("/cache/song.mp3", downloaded.path)
        assertTrue(downloaded.isDownloaded)
        assertEquals(MusicSource.WEBDAV, downloaded.source)
    }
}

class MusicSourceTest {

    @Test
    fun `MusicSource has LOCAL and WEBDAV values`() {
        assertEquals(2, MusicSource.values().size)
        assertEquals(MusicSource.LOCAL, MusicSource.valueOf("LOCAL"))
        assertEquals(MusicSource.WEBDAV, MusicSource.valueOf("WEBDAV"))
    }
}
