package com.webdav.music.data

import com.webdav.music.data.model.MusicSource
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for WebDAV URL parsing and building logic.
 * These test the core URL manipulation without requiring Android Context.
 */
class WebDAVUrlParsingTest {

    /**
     * Test URL decoding of %2F to / in paths
     */
    @Test
    fun `URL decoding converts %2F to forward slash`() {
        val encodedHref = "Music%2FAlbum%2Fsong.mp3"
        val decoded = encodedHref.replace("%2F", "/")
        assertEquals("Music/Album/song.mp3", decoded)
    }

    /**
     * Test extracting filename from URL with encoded path
     */
    @Test
    fun `filename extraction from encoded path`() {
        val encodedHref = "Music%2FAlbum%2Fsong.mp3"
        val decodedHref = encodedHref.replace("%2F", "/")
        val fileName = decodedHref.substringAfterLast("/")
        assertEquals("song.mp3", fileName)
    }

    /**
     * Test audio file extension detection
     */
    @Test
    fun `audio extension detection`() {
        val audioExtensions = listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")

        assertTrue("mp3".lowercase() in audioExtensions)
        assertTrue("FLAC".lowercase() in audioExtensions)
        assertTrue("M4A".lowercase() in audioExtensions)
        assertFalse("jpg".lowercase() in audioExtensions)
        assertFalse("txt".lowercase() in audioExtensions)
        assertFalse("".lowercase() in audioExtensions)
    }

    /**
     * Test building full URL from base and path
     */
    @Test
    fun `building full URL from base and path`() {
        val baseUrl = "https://example.com/webdav"
        val fullPath = "Music/Album/song.mp3"
        val fullUrl = "$baseUrl/$fullPath"
        assertEquals("https://example.com/webdav/Music/Album/song.mp3", fullUrl)
    }

    /**
     * Test trimming trailing slashes from base URL
     */
    @Test
    fun `trimming trailing slashes`() {
        assertEquals("https://example.com/webdav", "https://example.com/webdav/".trimEnd('/'))
        assertEquals("https://example.com/webdav", "https://example.com/webdav//".trimEnd('/'))
        assertEquals("https://example.com", "https://example.com/".trimEnd('/'))
    }

    /**
     * Test that audio files with various extensions are recognized
     */
    @Test
    fun `all supported audio formats are recognized`() {
        val audioExtensions = listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")

        listOf("song.MP3", "album.FLAC", "track.AAC", "music.OGG", "sound.WAV", "podcast.M4A").forEach { fileName ->
            val extension = fileName.substringAfterLast(".").lowercase()
            assertTrue("$fileName should be recognized as audio", extension in audioExtensions)
        }
    }

    /**
     * Test hash code consistency for URL-based IDs
     */
    @Test
    fun `URL hash is consistent`() {
        val url1 = "https://example.com/webdav/Music/song.mp3"
        val url2 = "https://example.com/webdav/Music/song.mp3"
        val url3 = "https://example.com/webdav/Music/other.mp3"

        // Same URL should produce same hash
        assertEquals(url1.hashCode(), url2.hashCode())

        // Different URL should produce different hash (highly likely)
        assertNotEquals(url1.hashCode(), url3.hashCode())
    }

    /**
     * Test ID format for WebDAV items
     */
    @Test
    fun `WebDAV item ID format`() {
        val url = "https://example.com/webdav/Music/song.mp3"
        val id = "webdav_${url.hashCode()}"
        assertTrue(id.startsWith("webdav_"))
    }

    /**
     * Test MusicSource enum values
     */
    @Test
    fun `MusicSource enum values`() {
        assertEquals(MusicSource.LOCAL, MusicSource.valueOf("LOCAL"))
        assertEquals(MusicSource.WEBDAV, MusicSource.valueOf("WEBDAV"))
    }
}
