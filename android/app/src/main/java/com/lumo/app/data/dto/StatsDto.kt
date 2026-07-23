package com.lumo.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    val total_study_time: Int = 0,
    val streak: Int = 0,
    val study_sessions: List<StudySessionDto> = emptyList(),
)

@Serializable
data class StudySessionDto(
    val id: String,
    val task_id: String? = null,
    val plan_id: String? = null,
    val started_at: String,
    val duration_seconds: Int = 0,
    val pomodoro_count: Int = 0,
)

@Serializable
data class StudyTrendDto(
    val period: String = "week",
    val data: Map<String, Int> = emptyMap(),
    val total: Int = 0,
)

@Serializable
data class CheckinDayDto(
    val date: String,
    val task_ids_completed: String? = null,
)

@Serializable
data class KnowledgePointDto(
    val id: String,
    val plan_id: String,
    val name: String,
    val mastery_level: Int = 0,
    val last_reviewed: String? = null,
    val created_at: String = "",
)
