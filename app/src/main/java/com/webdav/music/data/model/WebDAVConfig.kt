package com.webdav.music.data.model

import java.util.UUID

data class WebDAVConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "服务器"
) {
    fun isValid(): Boolean = serverUrl.isNotBlank() && username.isNotBlank()
}
