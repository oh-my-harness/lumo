package com.lumo.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(
    val id: String,
    val title: String = "",
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class MessageDto(
    val id: String,
    val session_id: String,
    val role: String,
    val content: String,
    val created_at: String = "",
)

@Serializable
data class QuickPromptDto(
    val key: String,
    val label: String,
    val prompt: String,
)

@Serializable
data class ProviderConfigDto(
    val provider_type: String,
    val api_key: String,
    val base_url: String = "",
    val model: String,
)
