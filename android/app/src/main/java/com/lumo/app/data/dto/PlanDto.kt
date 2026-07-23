package com.lumo.app.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PlanDto(
    val id: String,
    val title: String,
    val goal: String,
    val daily_minutes: Int = 60,
    val start_date: String? = null,
    val end_date: String? = null,
    val status: String = "active",
    val created_at: String = "",
)

@Serializable
data class TaskDto(
    val id: String,
    val plan_id: String,
    val week_num: Int,
    val day_of_week: Int? = null,
    val title: String,
    val description: String? = null,
    val knowledge_points: String? = null,
    val order_idx: Int = 0,
    val status: String = "pending",
    val created_at: String = "",
    // Extra field added by get_today_tasks
    val plan_title: String? = null,
)

@Serializable
data class PlanGenerationResultDto(
    val weeks: List<JsonElement>? = null,
    val tasks: List<JsonElement>? = null,
    val verified: Boolean? = null,
    val cost: Double? = null,
    val plan_id: String? = null,
)
