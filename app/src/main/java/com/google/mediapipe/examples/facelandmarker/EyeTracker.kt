package com.google.mediapipe.examples.facelandmarker

import android.graphics.PointF
import android.util.DisplayMetrics
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Eye tracking utility that calculates eye position based on eye landmarks
 * and maps it to screen coordinates for mouse control
 */
class EyeTracker(private val displayMetrics: DisplayMetrics) {
    
    companion object {
        private const val TAG = "EyeTracker"
        
        // MediaPipe face landmark indices for eyes (468 landmarks)
        // Left eye (from user's perspective, right eye on face) - indices 33-42
        // Right eye (from user's perspective, left eye on face) - indices 362-373
        
        // Eye line landmarks - these form the eye contour
        // Left eye contour (right eye from user's view)
        private val LEFT_EYE_LINE_INDICES = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
        
        // Right eye contour (left eye from user's view)  
        private val RIGHT_EYE_LINE_INDICES = listOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
        
        // Pupil centers (iris landmarks)
        private const val LEFT_EYE_PUPIL = 468 // Iris center for left eye (if available, else use 33+7)/2
        private const val RIGHT_EYE_PUPIL = 473 // Iris center for right eye (if available, else use 362+263)/2
    }
    
    data class EyeRegion(
        val center: PointF,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val width: Float,
        val height: Float
    )
    
    data class TrackingResult(
        val leftEyeRegion: EyeRegion?,
        val rightEyeRegion: EyeRegion?,
        val combinedCenter: PointF?,
        val screenX: Float,
        val screenY: Float
    )
    
    /**
     * Calculate eye region based on eye line landmarks
     * Returns a rectangle area in the middle of the eye
     */
    private fun calculateEyeRegion(
        landmarks: List<NormalizedLandmark>,
        eyeIndices: List<Int>
    ): EyeRegion? {
        if (eyeIndices.isEmpty()) return null
        
        val eyePoints = eyeIndices.mapNotNull { index ->
            landmarks.getOrNull(index)
        }
        
        if (eyePoints.isEmpty()) return null
        
        // Calculate bounding box
        val left = eyePoints.minOf { it.x() }
        val right = eyePoints.maxOf { it.x() }
        val top = eyePoints.minOf { it.y() }
        val bottom = eyePoints.maxOf { it.y() }
        
        val width = right - left
        val height = bottom - top
        
        // Calculate center of eye area
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        
        // Create a rectangle in the middle (80% of eye area)
        val rectWidth = width * 0.8f
        val rectHeight = height * 0.8f
        val rectLeft = centerX - rectWidth / 2f
        val rectRight = centerX + rectWidth / 2f
        val rectTop = centerY - rectHeight / 2f
        val rectBottom = centerY + rectHeight / 2f
        
        return EyeRegion(
            center = PointF(centerX, centerY),
            left = rectLeft,
            top = rectTop,
            right = rectRight,
            bottom = rectBottom,
            width = rectWidth,
            height = rectHeight
        )
    }
    
    /**
     * Get pupil position from landmarks
     * If specific pupil index doesn't exist, calculate center from eye landmarks
     */
    private fun getPupilPosition(
        landmarks: List<NormalizedLandmark>,
        pupilIndex: Int,
        eyeIndices: List<Int>
    ): PointF? {
        // Try to get specific pupil landmark
        val pupil = landmarks.getOrNull(pupilIndex)
        if (pupil != null) {
            return PointF(pupil.x(), pupil.y())
        }
        
        // Fallback: calculate center from eye region
        val eyePoints = eyeIndices.mapNotNull { landmarks.getOrNull(it) }
        if (eyePoints.isEmpty()) return null
        
        val centerX = eyePoints.map { it.x() }.average().toFloat()
        val centerY = eyePoints.map { it.y() }.average().toFloat()
        return PointF(centerX, centerY)
    }
    
    /**
     * Track eyes and calculate screen coordinates
     */
    fun trackEyes(landmarks: List<NormalizedLandmark>): TrackingResult {
        val leftEyeRegion = calculateEyeRegion(landmarks, LEFT_EYE_LINE_INDICES)
        val rightEyeRegion = calculateEyeRegion(landmarks, RIGHT_EYE_LINE_INDICES)
        
        val leftPupil = getPupilPosition(landmarks, LEFT_EYE_PUPIL, LEFT_EYE_LINE_INDICES)
        val rightPupil = getPupilPosition(landmarks, RIGHT_EYE_PUPIL, RIGHT_EYE_LINE_INDICES)
        
        // Calculate combined center from both eyes or use average
        val combinedCenter = when {
            leftEyeRegion != null && rightEyeRegion != null -> {
                // Average of both eye centers
                PointF(
                    (leftEyeRegion.center.x + rightEyeRegion.center.x) / 2f,
                    (leftEyeRegion.center.y + rightEyeRegion.center.y) / 2f
                )
            }
            leftEyeRegion != null -> leftEyeRegion.center
            rightEyeRegion != null -> rightEyeRegion.center
            else -> null
        }
        
        // If we have pupils, use weighted average (pupils are more accurate)
        val finalPoint = when {
            leftPupil != null && rightPupil != null -> {
                // Weighted average: 60% pupils, 40% eye regions
                val pupilCenter = PointF(
                    (leftPupil.x + rightPupil.x) / 2f,
                    (leftPupil.y + rightPupil.y) / 2f
                )
                if (combinedCenter != null) {
                    PointF(
                        pupilCenter.x * 0.6f + combinedCenter.x * 0.4f,
                        pupilCenter.y * 0.6f + combinedCenter.y * 0.4f
                    )
                } else {
                    pupilCenter
                }
            }
            leftPupil != null -> leftPupil
            rightPupil != null -> rightPupil
            else -> combinedCenter
        }
        
        // Map normalized coordinates (0-1) to screen coordinates
        val screenX = if (finalPoint != null) {
            finalPoint.x * displayMetrics.widthPixels
        } else {
            displayMetrics.widthPixels / 2f
        }
        
        val screenY = if (finalPoint != null) {
            finalPoint.y * displayMetrics.heightPixels
        } else {
            displayMetrics.heightPixels / 2f
        }
        
        return TrackingResult(
            leftEyeRegion = leftEyeRegion,
            rightEyeRegion = rightEyeRegion,
            combinedCenter = finalPoint,
            screenX = screenX,
            screenY = screenY
        )
    }
    
    /**
     * Get eye landmark indices for drawing
     */
    fun getLeftEyeIndices(): List<Int> {
        // Return line indices + pupil if available
        val pupil = if (LEFT_EYE_PUPIL <= 467) listOf(LEFT_EYE_PUPIL) else emptyList()
        return LEFT_EYE_LINE_INDICES + pupil
    }
    
    fun getRightEyeIndices(): List<Int> {
        // Return line indices + pupil if available
        val pupil = if (RIGHT_EYE_PUPIL <= 467) listOf(RIGHT_EYE_PUPIL) else emptyList()
        return RIGHT_EYE_LINE_INDICES + pupil
    }
}
