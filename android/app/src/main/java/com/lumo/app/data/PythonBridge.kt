package com.lumo.app.data

import com.chaquo.python.Python

/**
 * Thin wrapper around the Chaquopy Python module.
 * All calls go through `lumo.bridge` and return JSON strings.
 */
class PythonBridge(private val py: Python) {
    private val module = py.getModule("lumo.bridge")

    /** Call a bridge function and return its string result. */
    fun callString(func: String, vararg args: Any?): String {
        return module.callAttr(func, *args).toString()
    }

    /** Call a bridge function with no return value. */
    fun callUnit(func: String, vararg args: Any?) {
        module.callAttr(func, *args)
    }

    /** Call a bridge function and return its int result. */
    fun callInt(func: String, vararg args: Any?): Int {
        return module.callAttr(func, *args).toInt()
    }

    /** Public accessor for the raw module (for streamChat callback). */
    fun rawModule() = module
}
