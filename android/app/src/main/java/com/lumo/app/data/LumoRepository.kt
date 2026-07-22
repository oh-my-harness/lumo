@file:Suppress("UNCHECKED_CAST")
package com.lumo.app.data

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray

/**
 * Repository: Kotlin → Chaquopy → Python bridge.
 * All Python calls go through this class.
 */
class LumoRepository private constructor(private val py: Python) {

    companion object {
        @Volatile private var INSTANCE: LumoRepository? = null

        fun init(context: Context) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            val dataDir = context.filesDir.absolutePath + "/lumo"
            py.getModule("lumo.bridge").callAttr("init", dataDir)
            INSTANCE = LumoRepository(py)
        }

        fun get(): LumoRepository = INSTANCE ?: error("LumoRepository.init() not called")
    }

    private fun bridge() = py.getModule("lumo.bridge")

    // ── Provider Config ──
    fun saveProviderConfig(type: String, apiKey: String, baseUrl: String, model: String) {
        bridge().callAttr("save_provider_config", type, apiKey, baseUrl, model)
    }
    fun getProviderConfig(): Map<String, String>? {
        val result = bridge().callAttr("get_provider_config")
        return if (result.toString() == "None") null
        else (result.asMap() as Map<String, String>)
    }
    fun testConnection(type: String, apiKey: String, baseUrl: String, model: String): String {
        return bridge().callAttr("test_provider_connection", type, apiKey, baseUrl, model).toString()
    }

    // ── Sessions ──
    fun createSession(title: String): String = bridge().callAttr("create_session", title).toString()
    fun listSessions(): List<Map<String, String?>> {
        val list = bridge().callAttr("list_sessions").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun deleteSession(id: String) = bridge().callAttr("delete_session", id)
    fun updateSessionTitle(id: String, title: String) = bridge().callAttr("update_session_title", id, title)
    fun getMessages(sessionId: String): List<Map<String, String?>> {
        val list = bridge().callAttr("get_messages", sessionId).asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun searchMessages(query: String): List<Map<String, String?>> {
        val list = bridge().callAttr("search_messages", query).asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }

    // ── Chat ──
    fun startChat(sessionId: String) = bridge().callAttr("start_chat", sessionId)
    fun sendMessage(text: String): String = bridge().callAttr("send_message", text).toString()
    fun getQuickPrompts(): List<Map<String, String>> {
        val list = bridge().callAttr("get_quick_prompts").asList()
        return list.map { (it.asMap() as Map<String, String>) }
    }
    fun getChatHistory(): List<Map<String, String?>> {
        val list = bridge().callAttr("get_chat_history").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun abortChat() = bridge().callAttr("abort_chat")

    // ── Plans ──
    fun listPlans(): List<Map<String, String?>> {
        val list = bridge().callAttr("list_plans").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun createPlan(title: String, goal: String, dailyMinutes: Int, startDate: String, endDate: String): String =
        bridge().callAttr("create_plan", title, goal, dailyMinutes, startDate, endDate).toString()
    fun deletePlan(id: String) = bridge().callAttr("delete_plan", id)
    fun updatePlanStatus(id: String, status: String) = bridge().callAttr("update_plan_status", id, status)
    fun getPlanTasks(planId: String): List<Map<String, String?>> {
        val list = bridge().callAttr("get_plan_tasks", planId).asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun updateTaskStatus(taskId: String, status: String) = bridge().callAttr("update_task_status", taskId, status)
    fun getTodayTasks(): List<Map<String, String?>> {
        val list = bridge().callAttr("get_today_tasks").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }

    // ── Notes ──
    fun listNotes(): List<Map<String, String?>> {
        val list = bridge().callAttr("list_notes").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun createNote(title: String, content: String): String =
        bridge().callAttr("create_note", title, content).toString()
    fun updateNote(id: String, title: String, content: String) =
        bridge().callAttr("update_note", id, title, content)
    fun deleteNote(id: String) = bridge().callAttr("delete_note", id)
    fun searchNotes(query: String): List<Map<String, String?>> {
        val list = bridge().callAttr("search_notes", query).asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun listFolders(): List<Map<String, String?>> {
        val list = bridge().callAttr("list_folders").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun createFolder(name: String): String = bridge().callAttr("create_folder", name).toString()

    // ── Quiz ──
    fun getQuizQuestions(): List<Map<String, String?>> {
        val list = bridge().callAttr("get_quiz_questions").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun getQuizErrors(): List<Map<String, String?>> {
        val list = bridge().callAttr("get_quiz_errors").asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun gradeAnswer(questionId: String, userAnswer: String): Map<String, Any?> {
        val result = bridge().callAttr("grade_answer", questionId, userAnswer)
        return (result.asMap() as Map<String, Any?>)
    }

    // ── Stats ──
    fun getStats(): Map<String, Any?> {
        val result = bridge().callAttr("get_stats")
        return (result.asMap() as Map<String, Any?>)
    }
    fun getStreak(): Int = bridge().callAttr("get_streak").toInt()
    fun getTotalStudyTime(): Int = bridge().callAttr("get_total_study_time").toInt()
    fun getCheckinHeatmap(month: String): List<Map<String, String?>> {
        val list = bridge().callAttr("get_checkin_heatmap", month).asList()
        return list.map { (it.asMap() as Map<String, String?>) }
    }
    fun checkinToday(taskIds: List<String>) {
        val jsonArr = JSONArray(taskIds).toString()
        bridge().callAttr("checkin_today", jsonArr)
    }
    fun recordPomodoro(taskId: String, planId: String, durationSec: Int, startedAt: String) {
        bridge().callAttr("record_pomodoro", taskId, planId, durationSec, startedAt)
    }
}
