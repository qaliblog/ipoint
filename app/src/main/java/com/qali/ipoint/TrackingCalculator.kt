package com.qali.ipoint

/**
 * Calculates final screen coordinates with all adjustments applied
 */
class TrackingCalculator(private val settings: SettingsManager, private val displayMetrics: android.util.DisplayMetrics) {
    
    fun calculateAdjustedPosition(result: EyeTracker.TrackingResult): Pair<Float, Float> {
        // Base position from eye tracking
        var adjustedX = result.screenX
        var adjustedY = result.screenY
        
        // Apply eye position X effect (can be positive or negative)
        val eyePosXOffset = (result.eyePositionX - 0.5f) * settings.eyePositionXEffect * settings.eyePositionXMultiplier
        adjustedX += eyePosXOffset * displayMetrics.widthPixels
        
        // Apply eye position Y effect (average Y position)
        val eyePosYOffset = (result.eyePositionY - 0.5f) * settings.eyePositionYEffect * settings.eyePositionYMultiplier
        adjustedY += eyePosYOffset * displayMetrics.heightPixels
        
        // Apply distance-based adjustments (distance increases as eye area decreases)
        // Distance is already normalized: 0 = closest (biggest area), increases as farther
        val distanceXOffset = result.eyeArea * settings.distanceXMultiplier * displayMetrics.widthPixels
        val distanceYOffset = result.eyeArea * settings.distanceYMultiplier * displayMetrics.heightPixels
        adjustedX += distanceXOffset
        adjustedY += distanceYOffset
        
        // Apply movement multipliers
        adjustedX *= settings.xMovementMultiplier
        adjustedY *= settings.yMovementMultiplier
        
        // Clamp to screen bounds
        adjustedX = adjustedX.coerceIn(0f, displayMetrics.widthPixels.toFloat())
        adjustedY = adjustedY.coerceIn(0f, displayMetrics.heightPixels.toFloat())
        
        return Pair(adjustedX, adjustedY)
    }
}
