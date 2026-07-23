package com.lumo.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumo.app.data.LumoRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test: Kotlin → Chaquopy → Python bridge → SQLite.
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest
 *
 * Verifies the full bridge layer on-device, covering CRUD operations
 * and error paths that Python unit tests can't catch (Chaquopy type
 * conversion, JNI overhead, real SQLite on Android).
 */
@RunWith(AndroidJUnit4::class)
class BridgeInstrumentedTest {

    @Before
    fun setup() {
        val ctx = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        LumoRepository.init(ctx)
    }

    // ── Provider Config ──

    @Test
    fun saveAndLoadProviderConfig() {
        val repo = LumoRepository.get()
        repo.saveProviderConfig("openai", "sk-test-123", "http://api.test.com/", "gpt-4o")
        val config = repo.getProviderConfig()
        assertNotNull(config)
        assertEquals("openai", config!!["provider_type"])
        assertEquals("sk-test-123", config["api_key"])
        assertEquals("gpt-4o", config["model"])
    }

    // ── Sessions ──

    @Test
    fun createAndListSessions() {
        val repo = LumoRepository.get()
        val sid = repo.createSession("Test Session")
        assertTrue(sid.isNotEmpty())
        val sessions = repo.listSessions()
        assertTrue(sessions.any { it["title"] == "Test Session" })
    }

    @Test
    fun deleteSession() {
        val repo = LumoRepository.get()
        val sid = repo.createSession("To Delete")
        repo.deleteSession(sid)
        val sessions = repo.listSessions()
        assertTrue(sessions.none { it["id"] == sid })
    }

    @Test
    fun updateSessionTitle() {
        val repo = LumoRepository.get()
        val sid = repo.createSession("Old Title")
        repo.updateSessionTitle(sid, "New Title")
        val sessions = repo.listSessions()
        assertEquals("New Title", sessions.first { it["id"] == sid }["title"])
    }

    // ── Plans ──

    @Test
    fun createAndListPlans() {
        val repo = LumoRepository.get()
        val pid = repo.createPlan("Test Plan", "Learn testing", 60, "2026-07-01", "2026-08-01")
        assertTrue(pid.isNotEmpty())
        val plans = repo.listPlans()
        assertTrue(plans.any { it["title"] == "Test Plan" })
    }

    @Test
    fun updatePlanStatus() {
        val repo = LumoRepository.get()
        val pid = repo.createPlan("Plan", "Goal", 30, "", "")
        repo.updatePlanStatus(pid, "paused")
        val plans = repo.listPlans()
        assertEquals("paused", plans.first { it["id"] == pid }["status"])
    }

    @Test
    fun deletePlan() {
        val repo = LumoRepository.get()
        val pid = repo.createPlan("To Delete", "Goal", 30, "", "")
        repo.deletePlan(pid)
        val plans = repo.listPlans()
        assertTrue(plans.none { it["id"] == pid })
    }

    // ── Notes ──

    @Test
    fun createAndListNotes() {
        val repo = LumoRepository.get()
        val nid = repo.createNote("Test Note", "Some content")
        assertTrue(nid.isNotEmpty())
        val notes = repo.listNotes()
        assertTrue(notes.any { it["title"] == "Test Note" })
    }

    @Test
    fun searchNotes() {
        val repo = LumoRepository.get()
        repo.createNote("Kotlin Tips", "Use val over var in Kotlin")
        val results = repo.searchNotes("Kotlin")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun updateAndDeleteNote() {
        val repo = LumoRepository.get()
        val nid = repo.createNote("Old", "old content")
        repo.updateNote(nid, "New", "new content")
        val notes = repo.listNotes()
        assertEquals("New", notes.first { it["id"] == nid }["title"])
        repo.deleteNote(nid)
        assertTrue(repo.listNotes().none { it["id"] == nid })
    }

    // ── Folders ──

    @Test
    fun createAndListFolders() {
        val repo = LumoRepository.get()
        val fid = repo.createFolder("Test Folder")
        assertTrue(fid.isNotEmpty())
        val folders = repo.listFolders()
        assertTrue(folders.any { it["name"] == "Test Folder" })
    }

    // ── Quiz ──

    @Test
    fun gradeCorrectAnswer() {
        val repo = LumoRepository.get()
        // Create a question via Python bridge
        val bridge = repo.pyBridge()
        val qid = bridge.callAttr("_ensure_store").callAttr(
            "create_question",
            "single_choice", "2+2=?", "[\"3\",\"4\",\"5\",\"6\"]", "4"
        ).toString()
        val result = repo.gradeAnswer(qid, "4")
        assertEquals(true, result["is_correct"])
    }

    @Test
    fun gradeWrongAnswer() {
        val repo = LumoRepository.get()
        val bridge = repo.pyBridge()
        val qid = bridge.callAttr("_ensure_store").callAttr(
            "create_question",
            "single_choice", "2+2=?", "[\"3\",\"4\",\"5\",\"6\"]", "4"
        ).toString()
        val result = repo.gradeAnswer(qid, "3")
        assertEquals(false, result["is_correct"])
    }

    @Test
    fun gradeNonexistentQuestion() {
        val repo = LumoRepository.get()
        val result = repo.gradeAnswer("nonexistent-id", "answer")
        assertEquals(false, result["is_correct"])
    }

    // ── Stats ──

    @Test
    fun getStatsReturnsValidStructure() {
        val repo = LumoRepository.get()
        val stats = repo.getStats()
        assertNotNull(stats["total_study_time"])
        assertNotNull(stats["streak"])
        assertNotNull(stats["study_sessions"])
    }

    @Test
    fun getStreakReturnsInt() {
        val repo = LumoRepository.get()
        val streak = repo.getStreak()
        assertTrue(streak >= 0)
    }

    @Test
    fun getTotalStudyTimeReturnsInt() {
        val repo = LumoRepository.get()
        val total = repo.getTotalStudyTime()
        assertTrue(total >= 0)
    }

    // ── Quick Prompts ──

    @Test
    fun getQuickPromptsReturnsNonEmptyList() {
        val repo = LumoRepository.get()
        val prompts = repo.getQuickPrompts()
        assertTrue(prompts.isNotEmpty())
        prompts.forEach { p ->
            assertNotNull(p["key"])
            assertNotNull(p["label"])
            assertNotNull(p["prompt"])
        }
    }

    // ── Today Tasks ──

    @Test
    fun getTodayTasksReturnsList() {
        val repo = LumoRepository.get()
        val tasks = repo.getTodayTasks()
        assertNotNull(tasks)
    }

    // ── Chat Error Paths ──

    @Test(expected = Exception::class)
    fun sendMessageWithoutChatThrows() {
        LumoRepository.get().sendMessage("test")
    }

    // ── checkin_today (tests the type mismatch fix) ──

    @Test
    fun checkinTodayAcceptsList() {
        val repo = LumoRepository.get()
        // Kotlin passes List<String>; Python must handle it (not just JSON string)
        repo.checkinToday(listOf("task1", "task2"))
        val month = java.time.LocalDate.now().toString().substring(0, 7)
        val heatmap = repo.getCheckinHeatmap(month)
        assertTrue(heatmap.isNotEmpty())
    }
}
