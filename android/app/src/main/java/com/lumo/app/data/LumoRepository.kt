package com.lumo.app.data

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.lumo.app.data.dto.*
import kotlinx.serialization.json.Json

/**
 * Repository: Kotlin → Chaquopy → Python bridge.
 * All Python calls return JSON strings, deserialized here to typed DTOs.
 */
class LumoRepository private constructor(private val bridge: PythonBridge) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        @Volatile private var instance: LumoRepository? = null
        fun init(context: Context) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            if (instance == null) synchronized(this) {
                if (instance == null) {
                    val repo = LumoRepository(PythonBridge(Python.getInstance()))
                    val dataDir = context.filesDir.absolutePath
                    repo.bridge.callUnit("init", dataDir)
                    // Auto-seed provider config from BuildConfig if none exists.
                    // This lets dev builds work without manual onboarding.
                    if (repo.getProviderConfig() == null) {
                        val key = com.lumo.app.BuildConfig.OPENAI_API_KEY
                        if (key.isNotEmpty() && key != "sk-placeholder") {
                            repo.saveProviderConfig(
                                "openai", key,
                                com.lumo.app.BuildConfig.OPENAI_API_BASE,
                                com.lumo.app.BuildConfig.OPENAI_MODEL,
                            )
                        }
                    }
                    instance = repo
                }
            }
        }
        fun get(): LumoRepository = instance ?: error("LumoRepository not initialized")
    }

    /** Public accessor for tests. */
    fun pyBridge() = bridge

    // ── Provider Config ──
    fun saveProviderConfig(type: String, apiKey: String, baseUrl: String, model: String) {
        bridge.callUnit("save_provider_config", type, apiKey, baseUrl, model)
    }
    fun getProviderConfig(): ProviderConfigDto? {
        val result = bridge.callString("get_provider_config")
        if (result == "null") return null
        return json.decodeFromString(result)
    }
    fun testConnection(type: String, apiKey: String, baseUrl: String, model: String): String {
        return bridge.callString("test_provider_connection", type, apiKey, baseUrl, model)
    }

    // ── Sessions ──
    fun createSession(title: String): String = bridge.callString("create_session", title)
    fun listSessions(): List<SessionDto> =
        json.decodeFromString(bridge.callString("list_sessions"))
    fun deleteSession(id: String) = bridge.callUnit("delete_session", id)
    fun updateSessionTitle(id: String, title: String) =
        bridge.callUnit("update_session_title", id, title)
    fun getMessages(sessionId: String): List<MessageDto> =
        json.decodeFromString(bridge.callString("get_messages", sessionId))
    fun searchMessages(query: String): List<MessageDto> =
        json.decodeFromString(bridge.callString("search_messages", query))

    // ── Chat ──
    fun startChat(sessionId: String) = bridge.callUnit("start_chat", sessionId)
    fun startChatWithTask(sessionId: String, taskId: String): String =
        bridge.callString("start_chat_with_task", sessionId, taskId)
    fun sendMessage(text: String): String = bridge.callString("send_message", text)
    fun streamChat(text: String, onToken: (String) -> Unit): String {
        val callback = object : Any() {
            @Suppress("unused")
            fun onToken(token: String) {
                onToken.invoke(token)
            }
        }
        return bridge.rawModule().callAttr("stream_chat", text, callback).toString()
    }
    fun getQuickPrompts(): List<QuickPromptDto> =
        json.decodeFromString(bridge.callString("get_quick_prompts"))
    fun getChatHistory(): List<MessageDto> =
        json.decodeFromString(bridge.callString("get_chat_history"))
    fun abortChat() = bridge.callUnit("abort_chat")
    fun saveConversationAsNote(sessionId: String, title: String = ""): String {
        return bridge.callString("save_conversation_as_note", sessionId, title)
    }

    // ── Plans ──
    fun listPlans(): List<PlanDto> =
        json.decodeFromString(bridge.callString("list_plans"))
    fun createPlan(title: String, goal: String, dailyMinutes: Int, startDate: String, endDate: String): String =
        bridge.callString("create_plan", title, goal, dailyMinutes, startDate, endDate)
    fun generatePlan(goal: String, dailyMinutes: Int, weekNum: Int = 1): PlanGenerationResultDto {
        return json.decodeFromString(bridge.callString("generate_plan", goal, dailyMinutes, weekNum))
    }
    fun deletePlan(id: String) = bridge.callUnit("delete_plan", id)
    fun updatePlanStatus(id: String, status: String) =
        bridge.callUnit("update_plan_status", id, status)
    fun getPlanTasks(planId: String): List<TaskDto> =
        json.decodeFromString(bridge.callString("get_plan_tasks", planId))
    fun updateTaskStatus(taskId: String, status: String) =
        bridge.callUnit("update_task_status", taskId, status)
    fun getTodayTasks(): List<TaskDto> =
        json.decodeFromString(bridge.callString("get_today_tasks"))

    // ── Notes ──
    fun listNotes(): List<NoteDto> =
        json.decodeFromString(bridge.callString("list_notes"))
    fun createNote(title: String, content: String): String =
        bridge.callString("create_note", title, content)
    fun updateNote(id: String, title: String, content: String) =
        bridge.callUnit("update_note", id, title, content)
    fun deleteNote(id: String) = bridge.callUnit("delete_note", id)
    fun searchNotes(query: String): List<NoteDto> =
        json.decodeFromString(bridge.callString("search_notes", query))
    fun aiSummarizeNote(noteId: String): String =
        bridge.callString("ai_summarize_note", noteId)
    fun summarizeNotes(noteIds: List<String>, title: String = ""): String {
        val jsonStr = org.json.JSONArray(noteIds).toString()
        return bridge.callString("summarize_notes", jsonStr, title)
    }
    fun listFolders(): List<FolderDto> =
        json.decodeFromString(bridge.callString("list_folders"))
    fun createFolder(name: String): String = bridge.callString("create_folder", name)

    // ── Quiz ──
    fun getQuizQuestions(): List<QuestionDto> =
        json.decodeFromString(bridge.callString("get_quiz_questions"))
    fun getQuizErrors(): List<WrongAnswerDto> =
        json.decodeFromString(bridge.callString("get_quiz_errors"))
    fun gradeAnswer(questionId: String, userAnswer: String): GradeResultDto {
        return json.decodeFromString(bridge.callString("grade_answer", questionId, userAnswer))
    }
    fun generateQuiz(knowledgePoints: String, numQuestions: Int = 3, planId: String = "", taskId: String = ""): QuizGenerationResultDto {
        return json.decodeFromString(bridge.callString("generate_quiz", knowledgePoints, numQuestions, planId, taskId))
    }

    // ── Stats ──
    fun getStats(): StatsDto =
        json.decodeFromString(bridge.callString("get_stats"))
    fun getStreak(): Int = bridge.callInt("get_streak")
    fun getTotalStudyTime(): Int = bridge.callInt("get_total_study_time")
    fun getCheckinHeatmap(month: String): List<CheckinDayDto> =
        json.decodeFromString(bridge.callString("get_checkin_heatmap", month))
    fun getStudyTrend(period: String = "week"): StudyTrendDto =
        json.decodeFromString(bridge.callString("get_study_trend", period))
    fun getKnowledgeMastery(planId: String): List<KnowledgePointDto> =
        json.decodeFromString(bridge.callString("get_knowledge_mastery", planId))
    fun checkinToday(taskIds: List<String>) {
        val jsonStr = org.json.JSONArray(taskIds).toString()
        bridge.callUnit("checkin_today", jsonStr)
    }
    fun recordPomodoro(taskId: String, planId: String, durationSec: Int, startedAt: String) {
        bridge.callUnit("record_pomodoro", taskId, planId, durationSec, startedAt)
    }
}
