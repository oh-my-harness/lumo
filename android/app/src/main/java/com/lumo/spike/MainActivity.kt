package com.lumo.spike

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            textSize = 13f
            setPadding(48, 96, 48, 48)
            text = "Running Lumo integration tests..."
        }
        val scrollView = ScrollView(this).apply { addView(tv) }
        setContentView(scrollView)

        Thread {
            val results = mutableListOf<String>()

            try {
                val py = Python.getInstance()

                // Get app private storage dir
                val dataDir = filesDir.absolutePath + "/lumo"
                results.add("Data dir: $dataDir")
                runOnUiThread { tv.text = results.joinToString("\n") }

                // Run all integration tests
                val testModule = py.getModule("lumo_integration_test")
                val testResult = testModule.callAttr("run_all_tests", dataDir).toString()
                results.clear()
                results.add(testResult)
                runOnUiThread { tv.text = results.joinToString("\n") }

                // Now test with real API if configured
                val apiKey = BuildConfig.OPENAI_API_KEY
                val baseUrl = BuildConfig.OPENAI_API_BASE
                val model = BuildConfig.OPENAI_MODEL

                results.add("")
                results.add("--- Real API Test ---")
                results.add("API Key: ${apiKey.take(8)}...")
                results.add("Base URL: $baseUrl")
                results.add("Model: $model")
                runOnUiThread { tv.text = results.joinToString("\n") }

                // Test provider connection
                if (apiKey != "sk-placeholder") {
                    val bridge = py.getModule("lumo.bridge")
                    val initDir = filesDir.absolutePath + "/lumo_api"
                    bridge.callAttr("init", initDir)
                    bridge.callAttr("save_provider_config", "openai", apiKey, baseUrl, model)

                    results.add("Testing connection...")
                    runOnUiThread { tv.text = results.joinToString("\n") }

                    val connResult = bridge.callAttr("test_provider_connection", "openai", apiKey, baseUrl, model).toString()
                    results.add("Connection: $connResult")
                    runOnUiThread { tv.text = results.joinToString("\n") }

                    // Test real chat
                    results.add("Starting chat...")
                    runOnUiThread { tv.text = results.joinToString("\n") }

                    val sid = bridge.callAttr("create_session", "API Test").toString()
                    bridge.callAttr("start_chat", sid)
                    val response = bridge.callAttr("send_message", "用一句话解释什么是闭包").toString()
                    results.add("Chat response: $response")

                    val history = bridge.callAttr("get_chat_history").toString()
                    results.add("History: ${history.take(200)}")

                    results.add("")
                    results.add("=== ALL TESTS PASSED ===")
                } else {
                    results.add("(Set OPENAI_API_KEY to test real API)")
                    results.add("")
                    results.add("=== INTEGRATION TESTS PASSED ===")
                }

                runOnUiThread { tv.text = results.joinToString("\n") }

            } catch (e: Exception) {
                results.add("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                results.add(e.stackTraceToString().take(2000))
                runOnUiThread { tv.text = results.joinToString("\n") }
            }
        }.start()
    }
}
