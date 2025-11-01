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
package com.qali.ipoint

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.qali.ipoint.CameraForegroundService
import com.qali.ipoint.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel : MainViewModel by viewModels()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide action bar and make full screen
        supportActionBar?.hide()
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        // Keep screen on to prevent activity suspension
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Acquire wake lock to keep camera running in background
        // Use SCREEN_DIM_WAKE_LOCK which keeps CPU and screen on (better for camera)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "iPoint::CameraWakeLock"
        ).apply {
            // Use a longer timeout and set reference counted to keep it alive
            setReferenceCounted(false)
            acquire()
        }
        
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
    }
    
    override fun onResume() {
        super.onResume()
        // Renew wake lock if needed
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Don't release wake lock - keep it for background camera operation
        // Only release on destroy
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // DON'T stop the service - let it continue running in background
        // The service will persist even when app is closed and handle all operations
        // User can stop it manually via notification or settings
        android.util.Log.d("MainActivity", "Activity destroyed, but service continues running")
    }

    override fun onBackPressed() {
        finish()
    }
}
