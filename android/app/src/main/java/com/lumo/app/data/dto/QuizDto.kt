package com.lumo.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class QuestionDto(
    val id: String,
    val plan_id: String? = null,
    val task_id: String? = null,
    val question_type: String = "single_choice",
    val question: String,
    val options: String? = null,
    val answer: String,
    val explanation: String? = null,
    val knowledge_points: String? = null,
    val created_at: String = "",
)

@Serializable
data class WrongAnswerDto(
    val id: String,
    val question_id: String,
    val user_answer: String,
    val is_correct: Int,
    val error_reason: String? = null,
    val created_at: String = "",
    // Joined fields from quiz_questions
    val question: String? = null,
    val question_type: String? = null,
    val options: String? = null,
    val correct_answer: String? = null,
    val explanation: String? = null,
    val knowledge_points: String? = null,
)

@Serializable
data class GradeResultDto(
    val is_correct: Boolean,
    val explanation: String = "",
)

@Serializable
data class QuizGenerationResultDto(
    val questions: List<QuizQuestionItemDto>? = null,
    val verified: Boolean? = null,
    val cost: Double? = null,
    val question_ids: List<String>? = null,
)

@Serializable
data class QuizQuestionItemDto(
    val type: String = "single_choice",
    val question: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    val explanation: String = "",
    val knowledge_points: List<String> = emptyList(),
)
