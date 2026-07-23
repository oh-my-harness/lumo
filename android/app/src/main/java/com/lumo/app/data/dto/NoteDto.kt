package com.lumo.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class NoteDto(
    val id: String,
    val folder_id: String? = null,
    val title: String,
    val content: String = "",
    val source: String = "manual",
    val linked_kp: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val parent_id: String? = null,
    val created_at: String = "",
)
