package com.qali.ipoint

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Accessibility service for mouse control
 * This service allows the app to control the mouse/cursor anywhere on the device
 */
class MouseControlService : AccessibilityService() {
    
    companion object {
        private const val TAG = "MouseControlService"
        private var instance: MouseControlService? = null
        
        fun getInstance(): MouseControlService? = instance
        
        fun moveCursor(x: Float, y: Float) {
            instance?.performMouseMove(x, y)
        }
        
        fun performClick() {
            instance?.performMouseClick()
        }
    }
    
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private val smoothingFactor = 0.7f // Smoothing factor for cursor movement
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "MouseControlService connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "MouseControlService destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for mouse control
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "MouseControlService interrupted")
    }
    
    /**
     * Move cursor using GestureDescription (Android 7.0+)
     */
    fun performMouseMove(x: Float, y: Float) {
        try {
            // Smooth the movement
            val smoothedX = lastX + (x - lastX) * (1 - smoothingFactor)
            val smoothedY = lastY + (y - lastY) * (1 - smoothingFactor)
            
            lastX = smoothedX
            lastY = smoothedY
            
            // Use dispatchGesture for Android 7.0+
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        android.graphics.Path().apply {
                            moveTo(smoothedX, smoothedY)
                            lineTo(smoothedX, smoothedY)
                        },
                        0,
                        50 // Short duration
                    )
                )
                .build()
                .let { gesture ->
                    dispatchGesture(gesture, null, null)
                }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error moving cursor: ${e.message}", e)
        }
    }
    
    /**
     * Perform a click at the current cursor position
     */
    fun performMouseClick() {
        try {
            val clickPath = android.graphics.Path().apply {
                moveTo(lastX, lastY)
                lineTo(lastX, lastY)
            }
            
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        clickPath,
                        0,
                        100
                    )
                )
                .build()
            
            dispatchGesture(gesture, object : 
                android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Click completed")
                }
                
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Click cancelled")
                }
            }, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click: ${e.message}", e)
        }
    }
    
}
