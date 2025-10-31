package com.qali.ipoint

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages settings for eye tracking mouse control
 */
class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // X and Y movement multipliers
    var xMovementMultiplier: Float
        get() = prefs.getFloat(KEY_X_MOVEMENT_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_X_MOVEMENT_MULTIPLIER, value).apply()
    
    var yMovementMultiplier: Float
        get() = prefs.getFloat(KEY_Y_MOVEMENT_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_Y_MOVEMENT_MULTIPLIER, value).apply()
    
    // Eye position X effect - range amplifier (0 = no effect, higher = more range)
    var eyePositionXEffect: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_X_EFFECT, 0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_X_EFFECT, value).apply()
    
    var eyePositionXMultiplier: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_X_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_X_MULTIPLIER, value).apply()
    
    // Eye position Y effect - range amplifier (0 = no effect, higher = more range)
    var eyePositionYEffect: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_Y_EFFECT, 0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_Y_EFFECT, value).apply()
    
    var eyePositionYMultiplier: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_Y_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_Y_MULTIPLIER, value).apply()
    
    // Distance-based range multipliers (based on eye area)
    // 0 = no effect, positive = increases range when far, negative = reverse effect
    var distanceXMultiplier: Float
        get() = prefs.getFloat(KEY_DISTANCE_X_MULTIPLIER, 0f)
        set(value) = prefs.edit().putFloat(KEY_DISTANCE_X_MULTIPLIER, value).apply()
    
    var distanceYMultiplier: Float
        get() = prefs.getFloat(KEY_DISTANCE_Y_MULTIPLIER, 0f)
        set(value) = prefs.edit().putFloat(KEY_DISTANCE_Y_MULTIPLIER, value).apply()
    
    // Blink detection threshold (0.0-1.0, default 0.3 = 30% decrease)
    var blinkThreshold: Float
        get() = prefs.getFloat(KEY_BLINK_THRESHOLD, 0.3f)
        set(value) = prefs.edit().putFloat(KEY_BLINK_THRESHOLD, value.coerceIn(0.05f, 0.8f)).apply()
    
    // Use one eye for detection (true) or both eyes (false)
    var useOneEyeDetection: Boolean
        get() = prefs.getBoolean(KEY_USE_ONE_EYE, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_ONE_EYE, value).apply()
    
    companion object {
        private const val PREFS_NAME = "ipoint_settings"
        
        private const val KEY_X_MOVEMENT_MULTIPLIER = "x_movement_multiplier"
        private const val KEY_Y_MOVEMENT_MULTIPLIER = "y_movement_multiplier"
        private const val KEY_EYE_POSITION_X_EFFECT = "eye_position_x_effect"
        private const val KEY_EYE_POSITION_X_MULTIPLIER = "eye_position_x_multiplier"
        private const val KEY_EYE_POSITION_Y_EFFECT = "eye_position_y_effect"
        private const val KEY_EYE_POSITION_Y_MULTIPLIER = "eye_position_y_multiplier"
        private const val KEY_DISTANCE_X_MULTIPLIER = "distance_x_multiplier"
        private const val KEY_DISTANCE_Y_MULTIPLIER = "distance_y_multiplier"
        private const val KEY_BLINK_THRESHOLD = "blink_threshold"
        private const val KEY_USE_ONE_EYE = "use_one_eye"
    }
}
