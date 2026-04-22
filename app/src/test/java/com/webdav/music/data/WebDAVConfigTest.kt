package com.webdav.music.data

import com.webdav.music.data.model.WebDAVConfig
import org.junit.Assert.*
import org.junit.Test

class WebDAVConfigTest {

    @Test
    fun `isValid returns true when serverUrl and username are not blank`() {
        val config = WebDAVConfig(
            serverUrl = "https://example.com/webdav/",
            username = "user",
            password = "pass"
        )
        assertTrue(config.isValid())
    }

    @Test
    fun `isValid returns true when password is empty`() {
        val config = WebDAVConfig(
            serverUrl = "https://example.com/webdav/",
            username = "user",
            password = ""
        )
        assertTrue(config.isValid())
    }

    @Test
    fun `isValid returns false when serverUrl is blank`() {
        val config = WebDAVConfig(
            serverUrl = "",
            username = "user",
            password = "pass"
        )
        assertFalse(config.isValid())
    }

    @Test
    fun `isValid returns false when username is blank`() {
        val config = WebDAVConfig(
            serverUrl = "https://example.com/webdav/",
            username = "",
            password = "pass"
        )
        assertFalse(config.isValid())
    }

    @Test
    fun `isValid returns false when both serverUrl and username are blank`() {
        val config = WebDAVConfig(
            serverUrl = "",
            username = "",
            password = "pass"
        )
        assertFalse(config.isValid())
    }
}
