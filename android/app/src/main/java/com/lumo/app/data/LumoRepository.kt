@file:Suppress("UNCHECKED_CAST")
package com.lumo.app.data

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Repository: Kotlin → Chaquopy → Python bridge.
 * All Python calls go through this class.
 */
class LumoRepository private constructor(private val py: Python) {

    companion object {
        @Volatile private var instance: LumoRepository? = null
        fun init(context: Context) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            if (instance == null) synchronized(this) {
                if (instance == null) {
                    val repo = LumoRepository(Python.getInstance())
                    // Initialize Python bridge with app data directory
                    val dataDir = context.filesDir.absolutePath
                    repo.bridge().callAttr("init", dataDir)
                    instance = repo
                }
            }
        }
        fun get(): LumoRepository = instance ?: error("LumoRepository not initialized")
    }

    private fun bridge() = py.getModule("lumo.bridge")
    /** Public accessor for tests. */
    fun pyBridge() = bridge()

    /** Convert a Chaquopy PyObject map to Map<String, String?> with proper toString(). */
    private fun PyObject.toStringMap(): Map<String, String?> =
        this.asMap().entries.associate { (k, v) ->
            k.toString() to (if (v == null) null else v.toString())
        }

    /** Convert a Chaquopy PyObject list-of-maps to List<Map<String, String?>>. */
    private fun PyObject.toStringMapList(): List<Map<String, String?>> =
        this.asList().map { it.toStringMap() }

    /** Convert a Chaquopy PyObject map to Map<String, String> (non-null values). */
    private fun PyObject.toNonNullableStringMap(): Map<String, String> =
        this.asMap().entries.associate { (k, v) ->
            k.toString() to (v?.toString() ?: "")
        }

    // ── Provider Config ──
    fun saveProviderConfig(type: String, apiKey: String, baseUrl: String, model: String) {
        bridge().callAttr("save_provider_config", type, apiKey, baseUrl, model)
    }
    fun getProviderConfig(): Map<String, String>? {
        val result = bridge().callAttr("get_provider_config")
        if (result == null) return null
        return try { result.toNonNullableStringMap() } catch (e: Exception) { null }
    }
    fun testConnection(type: String, apiKey: String, baseUrl: String, model: String): String {
        return bridge().callAttr("test_provider_connection", type, apiKey, baseUrl, model).toString()
    }

    // ── Sessions ──
    fun createSession(title: String): String = bridge().callAttr("create_session", title).toString()
    fun listSessions(): List<Map<String, String?>> =
        bridge().callAttr("list_sessions").toStringMapList()
    fun deleteSession(id: String) = bridge().callAttr("delete_session", id)
    fun updateSessionTitle(id: String, title: String) = bridge().callAttr("update_session_title", id, title)
    fun getMessages(sessionId: String): List<Map<String, String?>> =
        bridge().callAttr("get_messages", sessionId).toStringMapList()
    fun searchMessages(query: String): List<Map<String, String?>> =
        bridge().callAttr("search_messages", query).toStringMapList()

    // ── Chat ──
    fun startChat(sessionId: String) = bridge().callAttr("start_chat", sessionId)
    fun sendMessage(text: String): String = bridge().callAttr("send_message", text).toString()
    fun streamChat(text: String, onToken: (String) -> Unit): String {
        val callback = object : Any() {
            @Suppress("unused")
            fun onToken(token: String) {
                onToken.invoke(token)
            }
        }
        return bridge().callAttr("stream_chat", text, callback).toString()
    }
    fun getQuickPrompts(): List<Map<String, String>> =
        bridge().callAttr("get_quick_prompts").asList()
            .map { it.toNonNullableStringMap() }
    fun getChatHistory(): List<Map<String, String?>> =
        bridge().callAttr("get_chat_history").toStringMapList()
    fun abortChat() = bridge().callAttr("abort_chat")
    fun saveConversationAsNote(sessionId: String, title: String = ""): String {
        return bridge().callAttr("save_conversation_as_note", sessionId, title).toString()
    }

    // ── Plans ──
    fun listPlans(): List<Map<String, String?>> =
        bridge().callAttr("list_plans").toStringMapList()
    fun createPlan(title: String, goal: String, dailyMinutes: Int, startDate: String, endDate: String): String =
        bridge().callAttr("create_plan", title, goal, dailyMinutes, startDate, endDate).toString()
    fun generatePlan(goal: String, dailyMinutes: Int, weekNum: Int = 1): Map<String, Any?> {
        val result = bridge().callAttr("generate_plan", goal, dailyMinutes, weekNum)
        return result.asMap().entries.associate { (k, v) ->
            k.toString() to (v as? Any)
        }
    }
    fun deletePlan(id: String) = bridge().callAttr("delete_plan", id)
    fun updatePlanStatus(id: String, status: String) = bridge().callAttr("update_plan_status", id, status)
    fun getPlanTasks(planId: String): List<Map<String, String?>> =
        bridge().callAttr("get_plan_tasks", planId).toStringMapList()
    fun updateTaskStatus(taskId: String, status: String) = bridge().callAttr("update_task_status", taskId, status)
    fun getTodayTasks(): List<Map<String, String?>> =
        bridge().callAttr("get_today_tasks").toStringMapList()

    // ── Notes ──
    fun listNotes(): List<Map<String, String?>> =
        bridge().callAttr("list_notes").toStringMapList()
    fun createNote(title: String, content: String): String =
        bridge().callAttr("create_note", title, content).toString()
    fun updateNote(id: String, title: String, content: String) =
        bridge().callAttr("update_note", id, title, content)
    fun deleteNote(id: String) = bridge().callAttr("delete_note", id)
    fun searchNotes(query: String): List<Map<String, String?>> =
        bridge().callAttr("search_notes", query).toStringMapList()
    fun aiSummarizeNote(noteId: String): String =
        bridge().callAttr("ai_summarize_note", noteId).toString()
    fun listFolders(): List<Map<String, String?>> =
        bridge().callAttr("list_folders").toStringMapList()
    fun createFolder(name: String): String = bridge().callAttr("create_folder", name).toString()

    // ── Quiz ──
    fun getQuizQuestions(): List<Map<String, String?>> =
        bridge().callAttr("get_quiz_questions").toStringMapList()
    fun getQuizErrors(): List<Map<String, String?>> =
        bridge().callAttr("get_quiz_errors").toStringMapList()
    fun gradeAnswer(questionId: String, userAnswer: String): Map<String, Any?> {
        val result = bridge().callAttr("grade_answer", questionId, userAnswer)
        return result.asMap().entries.associate { (k, v) ->
            val converted = when {
                v == null -> null
                v.toString() == "True" -> true
                v.toString() == "False" -> false
                else -> v as? Any
            }
            k.toString() to converted
        }
    }
    fun generateQuiz(knowledgePoints: String, numQuestions: Int = 3, planId: String = "", taskId: String = ""): Map<String, Any?> {
        val result = bridge().callAttr("generate_quiz", knowledgePoints, numQuestions, planId, taskId)
        return result.asMap().entries.associate { (k, v) ->
            k.toString() to (v as? Any)
        }
    }

    // ── Stats ──
    fun getStats(): Map<String, Any?> {
        val result = bridge().callAttr("get_stats")
        return result.asMap().entries.associate { (k, v) ->
            k.toString() to (v as? Any)
        }
    }
    fun getStreak(): Int = bridge().callAttr("get_streak").toInt()
    fun getTotalStudyTime(): Int = bridge().callAttr("get_total_study_time").toInt()
    fun getCheckinHeatmap(month: String): List<Map<String, String?>> =
        bridge().callAttr("get_checkin_heatmap", month).toStringMapList()
    fun checkinToday(taskIds: List<String>) {
        // Convert to JSON string — Chaquopy can't pass Kotlin List as Python list
        val json = org.json.JSONArray(taskIds).toString()
        bridge().callAttr("checkin_today", json)
    }
    fun recordPomodoro(taskId: String, planId: String, durationSec: Int, startedAt: String) {
        bridge().callAttr("record_pomodoro", taskId, planId, durationSec, startedAt)
    }
}
