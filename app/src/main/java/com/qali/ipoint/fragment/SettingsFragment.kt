package com.qali.ipoint.fragment

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
import java.text.DecimalFormat
import java.util.Locale

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager
    private val df = DecimalFormat("#.##")
    private var isLogcatVisible = false
    private val logcatUpdateListener: (String) -> Unit = { logText ->
        binding.logcatText.text = logText
        // Auto scroll to bottom
        binding.logcatScroll.post {
            binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
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
        // Register logcat listener
        LogcatManager.registerListener(logcatUpdateListener)
        LogcatManager.addLog("Settings opened", "Settings")
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister logcat listener
        LogcatManager.unregisterListener(logcatUpdateListener)
    }
    
    private fun setupLogcat() {
        // Set initial log text
        binding.logcatText.text = LogcatManager.getLogText()
        
        binding.toggleLogcat.setOnClickListener {
            isLogcatVisible = !isLogcatVisible
            binding.logcatContainer.visibility = if (isLogcatVisible) View.VISIBLE else View.GONE
            binding.toggleLogcat.text = if (isLogcatVisible) "Hide Logcat" else "Show Logcat"
            
            if (isLogcatVisible) {
                // Refresh log when showing
                binding.logcatText.text = LogcatManager.getLogText()
                binding.logcatScroll.post {
                    binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
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
            val newValue = (settingsManager.xMovementMultiplier - 0.1f).coerceAtLeast(0.1f)
            settingsManager.xMovementMultiplier = newValue
            updateValue(binding.xMovementValue, newValue)
            LogcatManager.addLog("X Movement Multiplier: ${df.format(newValue)}", "Settings")
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
            val newValue = (settingsManager.yMovementMultiplier - 0.1f).coerceAtLeast(0.1f)
            settingsManager.yMovementMultiplier = newValue
            updateValue(binding.yMovementValue, newValue)
            LogcatManager.addLog("Y Movement Multiplier: ${df.format(newValue)}", "Settings")
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
            { settingsManager.eyePositionXEffect = it.coerceAtLeast(0f) },
            "Eye Position X Range Effect",
            0.1f)
        
        binding.eyePosXEffectMinus.setOnClickListener {
            val newValue = (settingsManager.eyePositionXEffect - 0.1f).coerceAtLeast(0f)
            settingsManager.eyePositionXEffect = newValue
            updateValue(binding.eyePosXEffectValue, newValue)
            LogcatManager.addLog("Eye Position X Effect: ${df.format(newValue)} (0 = no effect)", "Settings")
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
            { settingsManager.eyePositionYEffect = it.coerceAtLeast(0f) },
            "Eye Position Y Range Effect",
            0.1f)
        
        binding.eyePosYEffectMinus.setOnClickListener {
            val newValue = (settingsManager.eyePositionYEffect - 0.1f).coerceAtLeast(0f)
            settingsManager.eyePositionYEffect = newValue
            updateValue(binding.eyePosYEffectValue, newValue)
            LogcatManager.addLog("Eye Position Y Effect: ${df.format(newValue)} (0 = no effect)", "Settings")
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
        updateValue(binding.distanceXValue, settingsManager.distanceXMultiplier)
        
        binding.distanceXMinus.setOnClickListener {
            val newValue = settingsManager.distanceXMultiplier - 0.1f
            settingsManager.distanceXMultiplier = newValue
            updateValue(binding.distanceXValue, newValue)
        }
        
        binding.distanceXPlus.setOnClickListener {
            val newValue = settingsManager.distanceXMultiplier + 0.1f
            settingsManager.distanceXMultiplier = newValue
            updateValue(binding.distanceXValue, newValue)
        }
        
        updateValue(binding.distanceYValue, settingsManager.distanceYMultiplier)
        
        binding.distanceYMinus.setOnClickListener {
            val newValue = settingsManager.distanceYMultiplier - 0.1f
            settingsManager.distanceYMultiplier = newValue
            updateValue(binding.distanceYValue, newValue)
        }
        
        binding.distanceYPlus.setOnClickListener {
            val newValue = settingsManager.distanceYMultiplier + 0.1f
            settingsManager.distanceYMultiplier = newValue
            updateValue(binding.distanceYValue, newValue)
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
