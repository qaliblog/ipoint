/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qali.ipoint.fragment

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_SETTLING
import androidx.viewpager2.widget.ViewPager2.ScrollState
import com.qali.ipoint.EyeTracker
import com.qali.ipoint.FaceLandmarkerHelper
import com.qali.ipoint.LogcatManager
import com.qali.ipoint.MainViewModel
import com.qali.ipoint.MouseControlService
import com.qali.ipoint.PointerOverlayService
import com.qali.ipoint.R
import com.qali.ipoint.SettingsManager
import com.qali.ipoint.TrackingCalculator
import com.qali.ipoint.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.toList
import kotlin.math.roundToInt

class CameraFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Face Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val faceBlendshapesResultAdapter by lazy {
        FaceBlendshapesResultAdapter()
    }

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT // Force front camera

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService
    
    /** Eye tracking */
    private lateinit var eyeTracker: EyeTracker
    private lateinit var settingsManager: SettingsManager
    private lateinit var trackingCalculator: TrackingCalculator
    private var isMouseControlEnabled = false
    private var hasCheckedAccessibilityOnResume = false
    private var isSettingsOpening = false

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
            return
        }

        // Re-check accessibility service status (but don't show prompt if already checked)
        if (!hasCheckedAccessibilityOnResume) {
            checkAccessibilityPermission(showPrompt = false) // Don't auto-open settings on resume
            hasCheckedAccessibilityOnResume = true
        }
        
        // Start the FaceLandmarkerHelper again when users come back
        // to the foreground (only if it was closed)
        if (this::faceLandmarkerHelper.isInitialized) {
            backgroundExecutor.execute {
                if (faceLandmarkerHelper.isClose()) {
                    faceLandmarkerHelper.setupFaceLandmarker()
                    LogcatManager.addLog("FaceLandmarkerHelper restarted", "Camera")
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        hasCheckedAccessibilityOnResume = false
        isSettingsOpening = false
        // Keep camera running in background for continuous pointer updates
        // Don't stop the face landmarker - let it continue processing
        if(this::faceLandmarkerHelper.isInitialized) {
            viewModel.setMaxFaces(faceLandmarkerHelper.maxNumFaces)
            viewModel.setMinFaceDetectionConfidence(faceLandmarkerHelper.minFaceDetectionConfidence)
            viewModel.setMinFaceTrackingConfidence(faceLandmarkerHelper.minFaceTrackingConfidence)
            viewModel.setMinFacePresenceConfidence(faceLandmarkerHelper.minFacePresenceConfidence)
            viewModel.setDelegate(faceLandmarkerHelper.currentDelegate)
            
            LogcatManager.addLog("App paused but keeping camera active for background tracking", "Camera")
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep camera running in background for continuous pointer updates
        // Don't stop the face landmarker - let it continue processing
        if(this::faceLandmarkerHelper.isInitialized) {
            viewModel.setMaxFaces(faceLandmarkerHelper.maxNumFaces)
            viewModel.setMinFaceDetectionConfidence(faceLandmarkerHelper.minFaceDetectionConfidence)
            viewModel.setMinFaceTrackingConfidence(faceLandmarkerHelper.minFaceTrackingConfidence)
            viewModel.setMinFacePresenceConfidence(faceLandmarkerHelper.minFacePresenceConfidence)
            viewModel.setDelegate(faceLandmarkerHelper.currentDelegate)
            
            LogcatManager.addLog("App paused but keeping camera active for background tracking", "Camera")
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize EyeTracker
        val displayMetrics = resources.displayMetrics
        eyeTracker = EyeTracker(displayMetrics)
        
        // Initialize Settings and Calculator
        settingsManager = SettingsManager(requireContext())
        trackingCalculator = TrackingCalculator(settingsManager, displayMetrics)
        
        // Set EyeTracker in OverlayView
        fragmentCameraBinding.overlay.setEyeTracker(eyeTracker)

        // Setup settings button - use FragmentManager directly instead of Navigation Component
        fragmentCameraBinding.settingsButton.setOnClickListener {
            // Prevent multiple rapid clicks
            if (isSettingsOpening) {
                LogcatManager.addLog("Settings opening already in progress, ignoring click", "Camera")
                return@setOnClickListener
            }
            
            Log.e(TAG, "=== SETTINGS BUTTON CLICKED ===")
            LogcatManager.addLog("=== Settings button clicked ===", "Camera")
            
            try {
                val activity = requireActivity()
                val fragmentManager = activity.supportFragmentManager
                
                // Check if settings fragment is already showing or in backstack
                val existingFragment = fragmentManager.findFragmentByTag("SettingsFragment")
                if (existingFragment != null) {
                    if (existingFragment.isVisible) {
                        LogcatManager.addLog("Settings already visible, closing...", "Camera")
                        fragmentManager.popBackStack("SettingsFragment", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        return@setOnClickListener
                    } else {
                        // Fragment exists but not visible - make it visible
                        fragmentManager.beginTransaction()
                            .show(existingFragment)
                            .addToBackStack("SettingsFragment")
                            .commit()
                        LogcatManager.addLog("Showing existing SettingsFragment", "Camera")
                        return@setOnClickListener
                    }
                }
                
                isSettingsOpening = true
                LogcatManager.addLog("Opening SettingsFragment using FragmentTransaction...", "Camera")
                
                // Create and show SettingsFragment directly
                val settingsFragment = com.qali.ipoint.fragment.SettingsFragment()
                val transaction = fragmentManager.beginTransaction()
                
                // Add to the fragment_container (which contains the NavHostFragment)
                // We'll add it on top, not replace
                transaction.add(R.id.fragment_container, settingsFragment, "SettingsFragment")
                transaction.addToBackStack("SettingsFragment")
                transaction.commitAllowingStateLoss() // Use commitAllowingStateLoss to prevent IllegalStateException
                
                LogcatManager.addLog("SettingsFragment transaction committed successfully!", "Camera")
                Log.e(TAG, "SettingsFragment transaction committed")
                
                // Reset flag after a short delay
                fragmentCameraBinding.settingsButton.postDelayed({
                    isSettingsOpening = false
                }, 500)
                
            } catch (e: Exception) {
                isSettingsOpening = false
                LogcatManager.addLog("Failed to open settings: ${e.message}", "Camera")
                Log.e(TAG, "Error opening settings", e)
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to open settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        LogcatManager.addLog("Settings button click listener set up", "Camera")
        Log.e(TAG, "Settings button click listener set up")
        
        // Check and request accessibility permission
        checkAccessibilityPermission()
        
        // Request overlay permission and start pointer service
        requestOverlayPermission()
        
        // Initialize logging
        LogcatManager.addLog("CameraFragment initialized", "Camera")

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the FaceLandmarkerHelper that will handle the inference - Force GPU
        backgroundExecutor.execute {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
                minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                maxNumFaces = viewModel.currentMaxFaces,
                currentDelegate = FaceLandmarkerHelper.DELEGATE_GPU, // Force GPU
                faceLandmarkerHelperListener = this
            )
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
                LogcatManager.addLog("Overlay permission requested", "Camera")
            } else {
                startPointerService()
            }
        } else {
            startPointerService()
        }
    }
    
    private fun startPointerService() {
        val intent = Intent(requireContext(), PointerOverlayService::class.java)
        // Use regular startService - the service will call startForeground itself
        requireContext().startService(intent)
        LogcatManager.addLog("Pointer overlay service started", "Camera")
    }
    
    private fun checkAccessibilityPermission(showPrompt: Boolean = true) {
        // First check if service instance is available (most reliable check)
        val serviceInstance = MouseControlService.getInstance()
        if (serviceInstance != null) {
            LogcatManager.addLog("MouseControlService instance found - service is running", "Camera")
            isMouseControlEnabled = true
            return
        }
        
        // Fallback to checking enabled services list
        val isEnabled = isAccessibilityServiceEnabled()
        if (!isEnabled) {
            LogcatManager.addLog("Accessibility service not enabled", "Camera")
            isMouseControlEnabled = false
            
            // Only show prompt if explicitly requested (not on resume)
            if (showPrompt && isResumed && isAdded) {
                Toast.makeText(requireContext(), "Please enable accessibility service for mouse control", Toast.LENGTH_LONG).show()
                
                // Open accessibility settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                LogcatManager.addLog("Opened accessibility settings", "Camera")
            }
        } else {
            LogcatManager.addLog("Accessibility service is enabled and ready", "Camera")
            isMouseControlEnabled = true
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java) as? AccessibilityManager
            ?: return false
        
        if (!accessibilityManager.isEnabled) {
            LogcatManager.addLog("Accessibility manager not enabled", "Camera")
            return false
        }
        
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val serviceComponent = ComponentName(requireContext(), MouseControlService::class.java)
        val servicePackage = serviceComponent.packageName
        val serviceClass = serviceComponent.className
        
        // More robust check: compare both package and class name, handling both short and fully qualified names
        val isEnabled = enabledServices.any { 
            val info = it.resolveInfo.serviceInfo
            val enabledComponent = ComponentName(info.packageName, info.name)
            
            // Direct component comparison
            enabledComponent == serviceComponent || 
            // Also check if package matches and class name matches (handles fully qualified names)
            (info.packageName == servicePackage && 
             (info.name == serviceClass || info.name.endsWith(serviceClass)))
        }
        
        if (!isEnabled) {
            LogcatManager.addLog("MouseControlService not in enabled list", "Camera")
            LogcatManager.addLog("Looking for: $servicePackage/$serviceClass", "Camera")
            LogcatManager.addLog("Enabled services count: ${enabledServices.size}", "Camera")
            enabledServices.forEach { service ->
                val info = service.resolveInfo.serviceInfo
                LogcatManager.addLog("  - ${info.packageName}/${info.name}", "Camera")
            }
        } else {
            LogcatManager.addLog("MouseControlService is enabled and ready", "Camera")
        }
        
        return isEnabled
    }

    // Removed bottom sheet controls - not needed for full screen app

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectFace(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // Bind camera to activity lifecycle (not fragment) to keep it running in background
            val activity = requireActivity()
            camera = cameraProvider.bindToLifecycle(
                activity, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            LogcatManager.addLog("Camera bound successfully", "Camera")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            LogcatManager.addLog("Camera binding failed: ${exc.message}", "Camera")
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        faceLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after face have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: FaceLandmarkerHelper.ResultBundle
    ) {
        // Process even when app is in background - this ensures continuous updates
        val faceLandmarksList = resultBundle.result.faceLandmarks()
        
        if (faceLandmarksList.isNotEmpty()) {
            // Track eyes and control mouse
            val landmarks = faceLandmarksList[0] // Use first face
            val trackingResult = eyeTracker.trackEyes(landmarks)
            
            // Apply all adjustments from settings
            val (adjustedX, adjustedY) = trackingCalculator.calculateAdjustedPosition(trackingResult)
            
            // Always update system-wide pointer overlay (works even in background)
            // This is the critical part - must happen every frame
            PointerOverlayService.updatePointerPosition(adjustedX, adjustedY)
            
            // Control mouse if accessibility is enabled
            if (isMouseControlEnabled) {
                MouseControlService.moveCursor(adjustedX, adjustedY)
            }
            
            // Update UI only if fragment is still active and visible
            if (isResumed && _fragmentCameraBinding != null) {
                activity?.runOnUiThread {
                    if (_fragmentCameraBinding != null) {
                        // Update pointer position on overlay
                        fragmentCameraBinding.overlay.setPointerPosition(adjustedX, adjustedY)

                        // Pass necessary information to OverlayView for drawing on the canvas
                        fragmentCameraBinding.overlay.setResults(
                            resultBundle.result,
                            resultBundle.inputImageHeight,
                            resultBundle.inputImageWidth,
                            RunningMode.LIVE_STREAM
                        )
                        // Force a redraw
                        fragmentCameraBinding.overlay.invalidate()
                    }
                }
            }
            
            // Log periodically (not every frame to avoid spam)
            if (System.currentTimeMillis() % 1000 < 50) { // Log roughly every 1000ms
                LogcatManager.addLog("Eye: (${adjustedX.toInt()}, ${adjustedY.toInt()}) | Area: ${String.format(Locale.US, "%.4f", trackingResult.eyeArea)} | Pos: (${String.format(Locale.US, "%.2f", trackingResult.eyePositionX)}, ${String.format(Locale.US, "%.2f", trackingResult.eyePositionY)})", "Tracking")
            }
        } else {
            // No face detected - hide pointer
            PointerOverlayService.updatePointerPosition(-1f, -1f)
            
            if (isResumed && _fragmentCameraBinding != null) {
                activity?.runOnUiThread {
                    if (_fragmentCameraBinding != null) {
                        fragmentCameraBinding.overlay.setPointerPosition(-1f, -1f)
                    }
                }
            }
        }
    }

    override fun onEmpty() {
        fragmentCameraBinding.overlay.setPointerPosition(-1f, -1f)
        PointerOverlayService.updatePointerPosition(-1f, -1f)
        fragmentCameraBinding.overlay.clear()
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()

            LogcatManager.addLog("Error: $error", "Error")
            if (errorCode == FaceLandmarkerHelper.GPU_ERROR) {
                LogcatManager.addLog("GPU error, but continuing with GPU", "Error")
            }
        }
    }
}
