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
    
    // Eye position X effect (can be positive or negative)
    var eyePositionXEffect: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_X_EFFECT, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_X_EFFECT, value).apply()
    
    var eyePositionXMultiplier: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_X_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_X_MULTIPLIER, value).apply()
    
    // Eye position Y effect (average Y position)
    var eyePositionYEffect: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_Y_EFFECT, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_Y_EFFECT, value).apply()
    
    var eyePositionYMultiplier: Float
        get() = prefs.getFloat(KEY_EYE_POSITION_Y_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_EYE_POSITION_Y_MULTIPLIER, value).apply()
    
    // Distance-based range multipliers (based on eye area)
    var distanceXMultiplier: Float
        get() = prefs.getFloat(KEY_DISTANCE_X_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DISTANCE_X_MULTIPLIER, value).apply()
    
    var distanceYMultiplier: Float
        get() = prefs.getFloat(KEY_DISTANCE_Y_MULTIPLIER, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DISTANCE_Y_MULTIPLIER, value).apply()
    
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
    }
}
