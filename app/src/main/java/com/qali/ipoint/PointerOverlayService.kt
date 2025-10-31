package com.qali.ipoint

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Service that displays a floating pointer overlay on top of all apps
 * This allows the pointer to be visible even when the app is in background
 */
class PointerOverlayService : Service() {
    
    companion object {
        private const val TAG = "PointerOverlayService"
        private var instance: PointerOverlayService? = null
        
        fun getInstance(): PointerOverlayService? = instance
        
        fun updatePointerPosition(x: Float, y: Float) {
            instance?.updatePointer(x, y)
        }
    }
    
    private var windowManager: WindowManager? = null
    private var pointerView: View? = null
    private var pointerLayout: FrameLayout? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createPointerView()
        
        Log.d(TAG, "PointerOverlayService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createPointerView() {
        pointerLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Create custom pointer view
        pointerView = PointerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(60, 60)
        }
        
        pointerLayout?.addView(pointerView)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        
        try {
            windowManager?.addView(pointerLayout, params)
            Log.d(TAG, "Pointer overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding pointer overlay: ${e.message}", e)
        }
    }
    
    fun updatePointer(x: Float, y: Float) {
        pointerLayout?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams
            params?.let {
                it.x = x.toInt() - 30 // Center the pointer (60/2)
                it.y = y.toInt() - 30
                
                try {
                    windowManager?.updateViewLayout(view, it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating pointer position: ${e.message}", e)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        pointerLayout?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing pointer overlay: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "PointerOverlayService destroyed")
    }
}
