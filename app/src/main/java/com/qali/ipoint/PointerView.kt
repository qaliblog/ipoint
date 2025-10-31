package com.qali.ipoint

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Custom view that draws a pointer/cursor
 */
class PointerView(context: Context) : View(context) {
    
    private val pointerPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val centerPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val outerPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 128
        isAntiAlias = true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val size = 20f
        
        // Draw outer circle
        canvas.drawCircle(centerX, centerY, size, outerPaint)
        
        // Draw crosshair
        canvas.drawLine(centerX - size, centerY, centerX + size, centerY, pointerPaint)
        canvas.drawLine(centerX, centerY - size, centerX, centerY + size, pointerPaint)
        
        // Draw center dot
        canvas.drawCircle(centerX, centerY, 5f, centerPaint)
    }
}
