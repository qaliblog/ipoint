package com.qali.ipoint

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Centralized logcat manager that can be accessed from multiple fragments
 */
object LogcatManager {
    
    private const val PREFS_NAME = "logcat_prefs"
    private const val MAX_LOG_LINES = 200
    private val logBuffer = Collections.synchronizedList(mutableListOf<String>())
    private val listeners = mutableListOf<(String) -> Unit>()
    
    fun addLog(message: String, tag: String = "iPoint") {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] [$tag] $message"
        
        synchronized(logBuffer) {
            logBuffer.add(logLine)
            
            // Keep only last maxLogLines
            if (logBuffer.size > MAX_LOG_LINES) {
                logBuffer.removeAt(0)
            }
        }
        
        // Notify listeners
        val fullLog = getLogText()
        synchronized(listeners) {
            listeners.forEach { it(fullLog) }
        }
        
        // Also log to system logcat
        Log.d(tag, message)
    }
    
    fun getLogText(): String {
        synchronized(logBuffer) {
            return logBuffer.joinToString("\n")
        }
    }
    
    fun registerListener(listener: (String) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
        // Immediately send current log
        listener(getLogText())
    }
    
    fun unregisterListener(listener: (String) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
    
    fun clear() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        synchronized(listeners) {
            listeners.forEach { it("") }
        }
    }
}
