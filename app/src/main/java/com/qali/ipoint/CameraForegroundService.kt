package com.qali.ipoint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.qali.ipoint.fragment.CameraFragment

/**
 * Foreground service to keep camera processing active in background
 * Shows a persistent notification with wake lock icon to indicate the app is running
 */
class CameraForegroundService : Service() {
    
    companion object {
        private const val TAG = "CameraForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "camera_foreground_channel"
        const val ACTION_TOGGLE_WAKELOCK = "com.qali.ipoint.TOGGLE_WAKELOCK"
        private var instance: CameraForegroundService? = null
        
        fun getInstance(): CameraForegroundService? = instance
        
        fun start(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null
    private var isWakeLockEnabled = true
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // No need for broadcast receiver - we'll handle toggle directly in onStartCommand
        
        // Acquire wake lock to keep app running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "iPoint::CameraForegroundWakeLock"
        ).apply {
            setReferenceCounted(false)
            try {
                acquire()
                isWakeLockEnabled = true
                android.util.Log.d(TAG, "Wake lock acquired successfully")
                com.qali.ipoint.LogcatManager.addLog("Wake lock acquired - MediaPipe will continue processing", "Service")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
                isWakeLockEnabled = false
            }
        }
        
        // Start as foreground service - this MUST be called in onCreate
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            android.util.Log.d(TAG, "Foreground service started with notification")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            // Try to stop self if we can't start foreground
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle wake lock toggle action from notification
        if (intent?.action == ACTION_TOGGLE_WAKELOCK) {
            toggleWakeLock()
        }
        
        // Ensure we're still in foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start foreground in onStartCommand: ${e.message}", e)
            }
        }
        
        // Renew wake lock if enabled and needed - this is critical to keep camera active
        if (isWakeLockEnabled) {
            wakeLock?.let {
                if (!it.isHeld) {
                    try {
                        it.acquire()
                        android.util.Log.d(TAG, "Wake lock renewed in onStartCommand")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to renew wake lock: ${e.message}", e)
                    }
                }
            } ?: run {
                // Wake lock is null - recreate it
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "iPoint::CameraForegroundWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    try {
                        acquire()
                        isWakeLockEnabled = true
                        android.util.Log.d(TAG, "Wake lock recreated and acquired")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
                        isWakeLockEnabled = false
                    }
                }
            }
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Active",
                NotificationManager.IMPORTANCE_DEFAULT // Changed to DEFAULT so it's more visible
            ).apply {
                description = "Keeps camera active for eye tracking"
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create toggle action for wake lock - use getService to send intent directly to service
        val toggleIntent = Intent(this, CameraForegroundService::class.java).apply {
            action = ACTION_TOGGLE_WAKELOCK
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use a better icon - battery/wake lock icon
        val iconRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.R.drawable.ic_lock_power_off
        } else {
            android.R.drawable.ic_dialog_info
        }
        
        val wakeLockStatus = if (isWakeLockEnabled) "ON" else "OFF"
        val toggleText = if (isWakeLockEnabled) "Turn OFF" else "Turn ON"
        val statusIcon = if (isWakeLockEnabled) {
            android.R.drawable.ic_lock_lock
        } else {
            android.R.drawable.ic_lock_power_off
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("?? iPoint - Wake Lock: $wakeLockStatus")
            .setContentText(if (isWakeLockEnabled) "Wake lock ON ? Camera active ? MediaPipe running" else "Wake lock OFF ? Camera may pause")
            .setSmallIcon(iconRes)
            .setLargeIcon(null) // No large icon to keep it compact
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Changed to DEFAULT so it's visible
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setAutoCancel(false) // Don't auto-cancel
            .addAction(
                NotificationCompat.Action.Builder(
                    statusIcon,
                    toggleText,
                    togglePendingIntent
                ).build()
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(if (isWakeLockEnabled) {
                    "Wake lock is ACTIVE to keep the camera running.\nMediaPipe landmark detection is active.\nCamera is running for continuous cursor control."
                } else {
                    "Wake lock is DISABLED.\nCamera may pause when device sleeps.\nTap to enable wake lock."
                }))
            .build()
    }
    
    private fun toggleWakeLock() {
        isWakeLockEnabled = !isWakeLockEnabled
        
        if (isWakeLockEnabled) {
            // Acquire wake lock
            wakeLock?.let {
                if (!it.isHeld) {
                    try {
                        it.acquire()
                        android.util.Log.d(TAG, "Wake lock toggled ON")
                        com.qali.ipoint.LogcatManager.addLog("Wake lock enabled - MediaPipe will continue processing", "Service")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
                        isWakeLockEnabled = false
                    }
                }
            } ?: run {
                // Wake lock is null - recreate it
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "iPoint::CameraForegroundWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    try {
                        acquire()
                        android.util.Log.d(TAG, "Wake lock created and acquired")
                        com.qali.ipoint.LogcatManager.addLog("Wake lock enabled - MediaPipe will continue processing", "Service")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
                        isWakeLockEnabled = false
                    }
                }
            }
        } else {
            // Release wake lock
            wakeLock?.let {
                if (it.isHeld) {
                    try {
                        it.release()
                        android.util.Log.d(TAG, "Wake lock toggled OFF")
                        com.qali.ipoint.LogcatManager.addLog("Wake lock disabled - MediaPipe may pause when device sleeps", "Service")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to release wake lock: ${e.message}", e)
                    }
                }
            }
        }
        
        // Update notification to reflect new state
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update notification: ${e.message}", e)
        }
    }
    
    fun updateNotification(text: String? = null) {
        // Reuse createNotification but update text
        val notification = createNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}
