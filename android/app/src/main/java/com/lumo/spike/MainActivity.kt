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
            textSize = 14f
            setPadding(48, 96, 48, 48)
            text = "Running tests..."
        }
        val scrollView = ScrollView(this).apply { addView(tv) }
        setContentView(scrollView)

        val apiKey = BuildConfig.OPENAI_API_KEY
        val baseUrl = BuildConfig.OPENAI_API_BASE
        val model = BuildConfig.OPENAI_MODEL

        Thread {
            val results = mutableListOf<String>()

            try {
                val py = Python.getInstance()
                val module = py.getModule("lumo_spike")

                // Test 1: import
                results.add("Test 1 (import): " + module.callAttr("check_import").toString())
                appendResults(tv, results)

                // Test 2: harness with provider
                results.add("Test 2 (harness): " +
                    module.callAttr("check_harness_builder", apiKey, baseUrl, model).toString())
                appendResults(tv, results)

                // Test 3: simple prompt (non-streaming)
                results.add("Test 3 (prompt): calling LLM...")
                appendResults(tv, results)
                val promptResult = module.callAttr("test_prompt", apiKey, baseUrl, model).toString()
                results.clear()
                results.add("Test 3 (prompt): $promptResult")
                appendResults(tv, results)

                // Test 4: workflow
                results.add("Test 4 (workflow): running...")
                appendResults(tv, results)
                val wfResult = module.callAttr("test_workflow", apiKey, baseUrl, model).toString()
                results.clear()
                results.add("Test 4 (workflow):\n$wfResult")
                appendResults(tv, results)

                results.add("\n=== ALL TESTS PASSED ===")
                appendResults(tv, results)

            } catch (e: Exception) {
                results.add("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                results.add(e.stackTraceToString().take(2000))
                appendResults(tv, results)
            }
        }.start()
    }

    private fun appendResults(tv: TextView, results: MutableList<String>) {
        val text = results.joinToString("\n\n")
        runOnUiThread { tv.text = text }
    }
}
