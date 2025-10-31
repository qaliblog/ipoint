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
        _binding?.let { binding ->
            binding.logcatText.text = logText
            // Auto scroll to bottom
            binding.logcatScroll.post {
                binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
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
        // Register logcat listener only if view is created
        if (_binding != null) {
            LogcatManager.registerListener(logcatUpdateListener)
            LogcatManager.addLog("Settings opened", "Settings")
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister logcat listener
        LogcatManager.unregisterListener(logcatUpdateListener)
    }
    
    private fun setupLogcat() {
        // Set initial log text - check binding first
        _binding?.let { binding ->
            try {
                binding.logcatText.text = LogcatManager.getLogText()
            } catch (e: Exception) {
                LogcatManager.addLog("Error setting initial logcat text: ${e.message}", "Settings")
                binding.logcatText.text = "Error loading logs: ${e.message}"
            }
            
            binding.toggleLogcat.setOnClickListener {
                isLogcatVisible = !isLogcatVisible
                binding.logcatContainer.visibility = if (isLogcatVisible) View.VISIBLE else View.GONE
                binding.copyLogcat.visibility = if (isLogcatVisible) View.VISIBLE else View.GONE
                binding.toggleLogcat.text = if (isLogcatVisible) "Hide Logcat" else "Show Logcat"
                
                if (isLogcatVisible) {
                    // Refresh log when showing
                    try {
                        binding.logcatText.text = LogcatManager.getLogText()
                        binding.logcatScroll.post {
                            binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
                        }
                    } catch (e: Exception) {
                        LogcatManager.addLog("Error refreshing logcat: ${e.message}", "Settings")
                    }
                }
            }
            
            binding.copyLogcat.setOnClickListener {
                try {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val logText = LogcatManager.getLogText()
                    val clip = ClipData.newPlainText("Logcat", logText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Logcat copied to clipboard", Toast.LENGTH_SHORT).show()
                    LogcatManager.addLog("Logcat copied to clipboard", "Settings")
                } catch (e: Exception) {
                    LogcatManager.addLog("Error copying logcat: ${e.message}", "Settings")
                    Toast.makeText(requireContext(), "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
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
            val newValue = settingsManager.xMovementMultiplier - 0.1f
            settingsManager.xMovementMultiplier = newValue
            updateValue(binding.xMovementValue, newValue)
            LogcatManager.addLog("X Movement Multiplier: ${df.format(newValue)} (negative reverses direction)", "Settings")
        }
        
        binding.xMovementPlus.setOnClickListener {
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
            val newValue = settingsManager.yMovementMultiplier - 0.1f
            settingsManager.yMovementMultiplier = newValue
            updateValue(binding.yMovementValue, newValue)
            LogcatManager.addLog("Y Movement Multiplier: ${df.format(newValue)} (negative reverses direction)", "Settings")
        }
        
        binding.yMovementPlus.setOnClickListener {
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
            val newValue = settingsManager.eyePositionXEffect - 0.1f
            settingsManager.eyePositionXEffect = newValue
            updateValue(binding.eyePosXEffectValue, newValue)
            LogcatManager.addLog("Eye Position X Effect: ${df.format(newValue)} (0 = no effect, negative reverses)", "Settings")
        }
        
        binding.eyePosXEffectPlus.setOnClickListener {
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
            val newValue = settingsManager.eyePositionXMultiplier - 0.1f
            settingsManager.eyePositionXMultiplier = newValue
            updateValue(binding.eyePosXMultValue, newValue)
        }
        
        binding.eyePosXMultPlus.setOnClickListener {
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
            val newValue = settingsManager.eyePositionYEffect - 0.1f
            settingsManager.eyePositionYEffect = newValue
            updateValue(binding.eyePosYEffectValue, newValue)
            LogcatManager.addLog("Eye Position Y Effect: ${df.format(newValue)} (0 = no effect, negative reverses)", "Settings")
        }
        
        binding.eyePosYEffectPlus.setOnClickListener {
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
            val newValue = settingsManager.eyePositionYMultiplier - 0.1f
            settingsManager.eyePositionYMultiplier = newValue
            updateValue(binding.eyePosYMultValue, newValue)
        }
        
        binding.eyePosYMultPlus.setOnClickListener {
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
            val newValue = settingsManager.distanceXMultiplier - 0.1f
            settingsManager.distanceXMultiplier = newValue
            updateValue(binding.distanceXValue, newValue)
            LogcatManager.addLog("Distance X Multiplier: ${df.format(newValue)} (0 = no effect, negative = reverse)", "Settings")
        }
        
        binding.distanceXPlus.setOnClickListener {
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
            val newValue = settingsManager.distanceYMultiplier - 0.1f
            settingsManager.distanceYMultiplier = newValue
            updateValue(binding.distanceYValue, newValue)
            LogcatManager.addLog("Distance Y Multiplier: ${df.format(newValue)} (0 = no effect, negative = reverse)", "Settings")
        }
        
        binding.distanceYPlus.setOnClickListener {
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
                    
                    if (!anyFocused) {
                        CameraFragment.setCursorMovementEnabled(true)
                        LogcatManager.addLog("Cursor movement re-enabled after typing", "Settings")
                    } else {
                        // If still focused, schedule again
                        scheduleReEnableCursor()
                    }
                }
            }
            // Wait 3 seconds after typing stops before re-enabling cursor (longer delay to prevent cursor jumping)
            handler.postDelayed(reEnableRunnable!!, 3000)
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
                // Hide keyboard and schedule re-enable with delay
                editText.clearFocus()
                scheduleReEnableCursor()
                true
            } else {
                false
            }
        }
        
        // Track when user starts/finishes editing
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isUserEditing = true
                // DISABLE cursor movement when user is typing
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
                // Select all text when focused for easy editing
                editText.post { editText.selectAll() }
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
                // Schedule re-enable with delay (don't immediately re-enable)
                scheduleReEnableCursor()
            }
        }
        
        // Also handle text changes to track editing state
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                isUserEditing = true
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isUserEditing = true
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
            }
            override fun afterTextChanged(s: Editable?) {
                isUserEditing = true
                CameraFragment.setCursorMovementEnabled(false)
                reEnableRunnable?.let { handler.removeCallbacks(it) }
                // Schedule delayed re-enable after user stops typing
                scheduleReEnableCursor()
            }
        })
    }
    
    private fun updateValue(editText: EditText, value: Float) {
        // Only update if not currently being edited by user
        // This prevents interference while user is typing
        if (!editText.isFocused || editText.text.toString().isEmpty()) {
            val currentText = editText.text.toString()
            val newText = df.format(value)
            // Only update if different to avoid cursor jumping
            if (currentText != newText) {
                editText.setText(newText)
            }
        }
    }
    
    private fun updateValue(textView: android.widget.TextView, value: Float) {
        textView.text = df.format(value)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
