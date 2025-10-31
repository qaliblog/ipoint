package com.qali.ipoint.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.qali.ipoint.LogcatManager
import com.qali.ipoint.R
import com.qali.ipoint.SettingsManager
import com.qali.ipoint.databinding.FragmentSettingsBinding
import com.qali.ipoint.fragment.CameraFragment
import java.text.DecimalFormat
import java.util.Locale

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager
    private val df = DecimalFormat("#.##")
    private var isLogcatVisible = false
    private val logcatUpdateListener: (String) -> Unit = { logText ->
        // Safely access binding - it might be null if fragment view is destroyed
        // Ensure we're on main thread
        _binding?.let { binding ->
            try {
                // Update on main thread
                binding.root.post {
                    if (_binding != null && isAdded) {
                        try {
                            binding.logcatText.text = logText
                            // Auto scroll to bottom
                            binding.logcatScroll.post {
                                if (_binding != null) {
                                    try {
                                        binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SettingsFragment", "Error scrolling logcat", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsFragment", "Error updating logcat text", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error in logcat listener", e)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Disable cursor movement when settings are open
        CameraFragment.setCursorMovementEnabled(false)
        
        settingsManager = SettingsManager(requireContext())
        
        // Setup back button - ensure it works reliably
        binding.backButton.setOnClickListener {
            try {
                if (isAdded && !requireActivity().isFinishing) {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    LogcatManager.addLog("Back button pressed, returning to camera", "Settings")
                }
            } catch (e: Exception) {
                LogcatManager.addLog("Error handling back button: ${e.message}", "Settings")
                // Fallback: use finish if fragment is in activity
                try {
                    parentFragmentManager.popBackStack()
                } catch (e2: Exception) {
                    LogcatManager.addLog("Failed to pop backstack: ${e2.message}", "Settings")
                }
            }
        }
        
        setupLogcat()
        setupMovementMultipliers()
        setupEyePositionEffects()
        setupDistanceMultipliers()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Disable cursor movement when settings are visible
        CameraFragment.setCursorMovementEnabled(false)
        
        // Register logcat listener only if view is created
        try {
            if (_binding != null && isAdded) {
                LogcatManager.registerListener(logcatUpdateListener)
                // Don't call addLog here to avoid potential listener issues
                android.util.Log.d("SettingsFragment", "Settings opened")
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Error in onResume", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Re-enable cursor movement when settings are closed
        CameraFragment.setCursorMovementEnabled(true)
        
        // Unregister logcat listener
        try {
            LogcatManager.unregisterListener(logcatUpdateListener)
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Error unregistering listener", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Re-enable cursor movement when settings view is destroyed
        CameraFragment.setCursorMovementEnabled(true)
        
        _binding = null
    }
    
    private fun setupLogcat() {
        // Set initial log text - check binding first
        _binding?.let { binding ->
            try {
                val logText = LogcatManager.getLogText()
                binding.logcatText.text = logText
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error setting initial logcat text", e)
                try {
                    binding.logcatText.text = "Error loading logs: ${e.message}"
                } catch (e2: Exception) {
                    android.util.Log.e("SettingsFragment", "Error setting error message", e2)
                }
            }
            
            binding.toggleLogcat.setOnClickListener {
                try {
                    if (!isAdded) return@setOnClickListener
                    
                    isLogcatVisible = !isLogcatVisible
                    binding.logcatContainer.visibility = if (isLogcatVisible) View.VISIBLE else View.GONE
                    binding.copyLogcat.visibility = if (isLogcatVisible) View.VISIBLE else View.GONE
                    binding.toggleLogcat.text = if (isLogcatVisible) "Hide Logcat" else "Show Logcat"
                    
                    if (isLogcatVisible) {
                        // Refresh log when showing
                        try {
                            val logText = LogcatManager.getLogText()
                            binding.logcatText.text = logText
                            
                            // Scroll to bottom after a short delay
                            binding.logcatScroll.postDelayed({
                                if (_binding != null && isAdded) {
                                    try {
                                        binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SettingsFragment", "Error scrolling", e)
                                    }
                                }
                            }, 100)
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsFragment", "Error refreshing logcat", e)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsFragment", "Error in toggle logcat", e)
                }
            }
            
            binding.copyLogcat.setOnClickListener {
                try {
                    // Ensure we're on main thread and fragment is still attached
                    if (!isAdded || context == null) {
                        return@setOnClickListener
                    }
                    
                    // Get log text first (on current thread - this should be safe)
                    val logText = try {
                        LogcatManager.getLogText()
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsFragment", "Error getting log text", e)
                        Toast.makeText(context, "Failed to get logs", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    // Ensure we run clipboard operation on main thread
                    binding.root.post {
                        if (!isAdded || context == null) return@post
                        
                        try {
                            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (clipboard == null) {
                                Toast.makeText(requireContext(), "Clipboard service unavailable", Toast.LENGTH_SHORT).show()
                                return@post
                            }
                            
                            val clip = ClipData.newPlainText("iPoint Logcat", logText)
                            clipboard.setPrimaryClip(clip)
                            
                            // Show toast on main thread
                            Toast.makeText(requireContext(), "Logcat copied to clipboard", Toast.LENGTH_SHORT).show()
                            
                            // Don't call LogcatManager.addLog here to avoid potential infinite loop
                            android.util.Log.d("SettingsFragment", "Logcat copied to clipboard (${logText.length} chars)")
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsFragment", "Clipboard error", e)
                            if (isAdded && context != null) {
                                Toast.makeText(requireContext(), "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsFragment", "Error in copy logcat", e)
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Failed to copy logs", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun setupMovementMultipliers() {
        setupValueEditor(binding.xMovementValue, 
            { settingsManager.xMovementMultiplier },
            { settingsManager.xMovementMultiplier = it },
            "X Movement Multiplier",
            0.1f)
        
        binding.xMovementMinus.setOnClickListener {
            // Clear focus first to prevent cursor jumping
            binding.xMovementValue.clearFocus()
            val newValue = settingsManager.xMovementMultiplier - 0.1f
            settingsManager.xMovementMultiplier = newValue
            updateValue(binding.xMovementValue, newValue)
            LogcatManager.addLog("X Movement Multiplier: ${df.format(newValue)} (negative reverses direction)", "Settings")
        }
        
        binding.xMovementPlus.setOnClickListener {
            // Clear focus first to prevent cursor jumping
            binding.xMovementValue.clearFocus()
            val newValue = settingsManager.xMovementMultiplier + 0.1f
            settingsManager.xMovementMultiplier = newValue
            updateValue(binding.xMovementValue, newValue)
            LogcatManager.addLog("X Movement Multiplier: ${df.format(newValue)}", "Settings")
        }
        
        setupValueEditor(binding.yMovementValue,
            { settingsManager.yMovementMultiplier },
            { settingsManager.yMovementMultiplier = it },
            "Y Movement Multiplier",
            0.1f)
        
        binding.yMovementMinus.setOnClickListener {
            // Clear focus first to prevent cursor jumping
            binding.yMovementValue.clearFocus()
            val newValue = settingsManager.yMovementMultiplier - 0.1f
            settingsManager.yMovementMultiplier = newValue
            updateValue(binding.yMovementValue, newValue)
            LogcatManager.addLog("Y Movement Multiplier: ${df.format(newValue)} (negative reverses direction)", "Settings")
        }
        
        binding.yMovementPlus.setOnClickListener {
            // Clear focus first to prevent cursor jumping
            binding.yMovementValue.clearFocus()
            val newValue = settingsManager.yMovementMultiplier + 0.1f
            settingsManager.yMovementMultiplier = newValue
            updateValue(binding.yMovementValue, newValue)
            LogcatManager.addLog("Y Movement Multiplier: ${df.format(newValue)}", "Settings")
        }
    }
    
    private fun setupEyePositionEffects() {
        setupValueEditor(binding.eyePosXEffectValue,
            { settingsManager.eyePositionXEffect },
            { settingsManager.eyePositionXEffect = it },
            "Eye Position X Range Effect",
            0.1f)
        
        binding.eyePosXEffectMinus.setOnClickListener {
            binding.eyePosXEffectValue.clearFocus()
            val newValue = settingsManager.eyePositionXEffect - 0.1f
            settingsManager.eyePositionXEffect = newValue
            updateValue(binding.eyePosXEffectValue, newValue)
            LogcatManager.addLog("Eye Position X Effect: ${df.format(newValue)} (0 = no effect, negative reverses)", "Settings")
        }
        
        binding.eyePosXEffectPlus.setOnClickListener {
            binding.eyePosXEffectValue.clearFocus()
            val newValue = settingsManager.eyePositionXEffect + 0.1f
            settingsManager.eyePositionXEffect = newValue
            updateValue(binding.eyePosXEffectValue, newValue)
            LogcatManager.addLog("Eye Position X Effect: ${df.format(newValue)} (increases X range)", "Settings")
        }
        
        setupValueEditor(binding.eyePosXMultValue,
            { settingsManager.eyePositionXMultiplier },
            { settingsManager.eyePositionXMultiplier = it },
            "Eye Position X Multiplier",
            0.1f)
        
        binding.eyePosXMultMinus.setOnClickListener {
            binding.eyePosXMultValue.clearFocus()
            val newValue = settingsManager.eyePositionXMultiplier - 0.1f
            settingsManager.eyePositionXMultiplier = newValue
            updateValue(binding.eyePosXMultValue, newValue)
        }
        
        binding.eyePosXMultPlus.setOnClickListener {
            binding.eyePosXMultValue.clearFocus()
            val newValue = settingsManager.eyePositionXMultiplier + 0.1f
            settingsManager.eyePositionXMultiplier = newValue
            updateValue(binding.eyePosXMultValue, newValue)
        }
        
        setupValueEditor(binding.eyePosYEffectValue,
            { settingsManager.eyePositionYEffect },
            { settingsManager.eyePositionYEffect = it },
            "Eye Position Y Range Effect",
            0.1f)
        
        binding.eyePosYEffectMinus.setOnClickListener {
            binding.eyePosYEffectValue.clearFocus()
            val newValue = settingsManager.eyePositionYEffect - 0.1f
            settingsManager.eyePositionYEffect = newValue
            updateValue(binding.eyePosYEffectValue, newValue)
            LogcatManager.addLog("Eye Position Y Effect: ${df.format(newValue)} (0 = no effect, negative reverses)", "Settings")
        }
        
        binding.eyePosYEffectPlus.setOnClickListener {
            binding.eyePosYEffectValue.clearFocus()
            val newValue = settingsManager.eyePositionYEffect + 0.1f
            settingsManager.eyePositionYEffect = newValue
            updateValue(binding.eyePosYEffectValue, newValue)
            LogcatManager.addLog("Eye Position Y Effect: ${df.format(newValue)} (increases Y range)", "Settings")
        }
        
        setupValueEditor(binding.eyePosYMultValue,
            { settingsManager.eyePositionYMultiplier },
            { settingsManager.eyePositionYMultiplier = it },
            "Eye Position Y Multiplier",
            0.1f)
        
        binding.eyePosYMultMinus.setOnClickListener {
            binding.eyePosYMultValue.clearFocus()
            val newValue = settingsManager.eyePositionYMultiplier - 0.1f
            settingsManager.eyePositionYMultiplier = newValue
            updateValue(binding.eyePosYMultValue, newValue)
        }
        
        binding.eyePosYMultPlus.setOnClickListener {
            binding.eyePosYMultValue.clearFocus()
            val newValue = settingsManager.eyePositionYMultiplier + 0.1f
            settingsManager.eyePositionYMultiplier = newValue
            updateValue(binding.eyePosYMultValue, newValue)
        }
    }
    
    private fun setupDistanceMultipliers() {
        setupValueEditor(binding.distanceXValue,
            { settingsManager.distanceXMultiplier },
            { settingsManager.distanceXMultiplier = it },
            "Distance X Range Multiplier",
            0.1f)
        
        binding.distanceXMinus.setOnClickListener {
            binding.distanceXValue.clearFocus()
            val newValue = settingsManager.distanceXMultiplier - 0.1f
            settingsManager.distanceXMultiplier = newValue
            updateValue(binding.distanceXValue, newValue)
            LogcatManager.addLog("Distance X Multiplier: ${df.format(newValue)} (0 = no effect, negative = reverse)", "Settings")
        }
        
        binding.distanceXPlus.setOnClickListener {
            binding.distanceXValue.clearFocus()
            val newValue = settingsManager.distanceXMultiplier + 0.1f
            settingsManager.distanceXMultiplier = newValue
            updateValue(binding.distanceXValue, newValue)
            LogcatManager.addLog("Distance X Multiplier: ${df.format(newValue)} (increases X range when far)", "Settings")
        }
        
        setupValueEditor(binding.distanceYValue,
            { settingsManager.distanceYMultiplier },
            { settingsManager.distanceYMultiplier = it },
            "Distance Y Range Multiplier",
            0.1f)
        
        binding.distanceYMinus.setOnClickListener {
            binding.distanceYValue.clearFocus()
            val newValue = settingsManager.distanceYMultiplier - 0.1f
            settingsManager.distanceYMultiplier = newValue
            updateValue(binding.distanceYValue, newValue)
            LogcatManager.addLog("Distance Y Multiplier: ${df.format(newValue)} (0 = no effect, negative = reverse)", "Settings")
        }
        
        binding.distanceYPlus.setOnClickListener {
            binding.distanceYValue.clearFocus()
            val newValue = settingsManager.distanceYMultiplier + 0.1f
            settingsManager.distanceYMultiplier = newValue
            updateValue(binding.distanceYValue, newValue)
            LogcatManager.addLog("Distance Y Multiplier: ${df.format(newValue)} (increases Y range when far)", "Settings")
        }
    }
    
    private fun setupValueEditor(
        editText: EditText,
        getValue: () -> Float,
        setValue: (Float) -> Unit,
        settingName: String,
        stepSize: Float
    ) {
        // Set initial value
        updateValue(editText, getValue())
        
        // Track if we should allow updates (prevent interference while typing)
        var isUserEditing = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var reEnableRunnable: Runnable? = null
        
        // Helper to re-enable cursor movement with delay
        // NOTE: Since cursor is disabled when settings are open, this is only for when editing
        fun scheduleReEnableCursor() {
            reEnableRunnable?.let { handler.removeCallbacks(it) }
            reEnableRunnable = Runnable {
                // Check if any EditText is still focused before re-enabling
                _binding?.let { binding ->
                    val anyFocused = listOf(
                        binding.xMovementValue,
                        binding.yMovementValue,
                        binding.eyePosXEffectValue,
                        binding.eyePosXMultValue,
                        binding.eyePosYEffectValue,
                        binding.eyePosYMultValue,
                        binding.distanceXValue,
                        binding.distanceYValue
                    ).any { editText -> editText.isFocused }
                    
                    // Don't re-enable cursor movement - it stays disabled while settings are open
                    // The cursor will be re-enabled when settings fragment is closed
                    if (anyFocused) {
                        // If still focused, schedule again
                        scheduleReEnableCursor()
                    }
                }
            }
            // Wait 5 seconds after typing stops (but cursor stays disabled while in settings)
            handler.postDelayed(reEnableRunnable!!, 5000)
        }
        
        // Handle manual input - only process when user explicitly finishes
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                isUserEditing = false
                try {
                    val inputValue = editText.text.toString().toFloatOrNull()
                    if (inputValue != null) {
                        setValue(inputValue)
                        updateValue(editText, inputValue) // Format the display
                        LogcatManager.addLog("$settingName: ${df.format(inputValue)}", "Settings")
                    } else {
                        // Invalid input, restore previous value
                        updateValue(editText, getValue())
                    }
                } catch (e: Exception) {
                    updateValue(editText, getValue())
                    LogcatManager.addLog("Invalid value for $settingName, restored", "Settings")
                }
                // Hide keyboard - cursor stays disabled while settings are open
                editText.clearFocus()
                true
            } else {
                false
            }
        }
        
            // Track when user starts/finishes editing
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isUserEditing = true
                // Force disable cursor movement when any EditText gains focus
                // This is critical to prevent cursor jumping while typing
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
                // Select all text when focused for easy editing
                editText.post { 
                    try {
                        editText.selectAll()
                        // Double-check cursor is still disabled after focus change
                        CameraFragment.setCursorMovementEnabled(false)
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsFragment", "Error in focus handler", e)
                    }
                }
            } else {
                // Only update when focus is lost AND user was editing
                if (isUserEditing) {
                    isUserEditing = false
                    try {
                        val inputValue = editText.text.toString().toFloatOrNull()
                        if (inputValue != null) {
                            setValue(inputValue)
                            updateValue(editText, inputValue) // Format the display
                            LogcatManager.addLog("$settingName: ${df.format(inputValue)}", "Settings")
                        } else {
                            // Invalid input, restore previous value
                            updateValue(editText, getValue())
                        }
                    } catch (e: Exception) {
                        updateValue(editText, getValue())
                    }
                }
                // Keep cursor disabled - settings fragment is still open
                CameraFragment.setCursorMovementEnabled(false)
            }
        }
        
        // Also handle text changes to track editing state
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                isUserEditing = true
                // Cursor is already disabled when settings are open
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
                // Immediately cancel any pending re-enable
                reEnableRunnable = null
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isUserEditing = true
                // Cursor is already disabled when settings are open
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
                // Immediately cancel any pending re-enable
                reEnableRunnable = null
            }
            override fun afterTextChanged(s: Editable?) {
                isUserEditing = true
                // Cursor is already disabled when settings are open
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
                // Don't schedule re-enable - cursor stays disabled while settings are open
            }
        })
    }
    
    private fun updateValue(editText: EditText, value: Float) {
        // NEVER update the text if the EditText is focused - this causes cursor to jump
        // Only update when the user is not actively editing
        if (editText.isFocused) {
            // User is typing - don't interfere at all
            return
        }
        
        val currentText = editText.text.toString()
        val newText = df.format(value)
        
        // Only update if different to avoid unnecessary changes
        if (currentText != newText) {
            // Save cursor position if there was a selection
            val selectionStart = editText.selectionStart
            val selectionEnd = editText.selectionEnd
            
            editText.setText(newText)
            
            // Restore cursor position if it was valid
            if (selectionStart >= 0 && selectionStart <= newText.length) {
                editText.setSelection(selectionStart.coerceAtMost(newText.length))
            }
        }
    }
    
    private fun updateValue(textView: android.widget.TextView, value: Float) {
        textView.text = df.format(value)
    }
    
}
