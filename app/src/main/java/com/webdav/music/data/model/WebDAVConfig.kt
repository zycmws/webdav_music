package com.webdav.music.data.model

data class WebDAVConfig(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    fun isValid(): Boolean = serverUrl.isNotBlank() && username.isNotBlank()
}