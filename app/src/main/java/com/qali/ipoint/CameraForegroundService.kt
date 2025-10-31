package com.qali.ipoint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Acquire wake lock to keep app running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "iPoint::CameraForegroundWakeLock"
        ).apply {
            setReferenceCounted(false)
            try {
                acquire()
                android.util.Log.d(TAG, "Wake lock acquired successfully")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
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
        // Ensure we're still in foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start foreground in onStartCommand: ${e.message}", e)
            }
        }
        
        // Renew wake lock if needed - this is critical to keep camera active
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
                    android.util.Log.d(TAG, "Wake lock recreated and acquired")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
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
        
        // Use a better icon - battery/wake lock icon
        val iconRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.R.drawable.ic_lock_power_off
        } else {
            android.R.drawable.ic_dialog_info
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("?? iPoint Active - Wake Lock")
            .setContentText("Wake lock acquired ? Camera active ? Eye tracking running")
            .setSmallIcon(iconRes)
            .setLargeIcon(null) // No large icon to keep it compact
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Changed to DEFAULT so it's visible
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setAutoCancel(false) // Don't auto-cancel
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Wake lock is active to keep the camera running.\nEye tracking is active. Camera is running for continuous cursor control."))
            .build()
    }
    
    fun updateNotification(text: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val iconRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.R.drawable.ic_lock_power_off
        } else {
            android.R.drawable.ic_dialog_info
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("?? iPoint Active - Wake Lock")
            .setContentText(text ?: "Wake lock acquired ? Camera active ? Eye tracking running")
            .setSmallIcon(iconRes)
            .setLargeIcon(null)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(text ?: "Wake lock is active to keep the camera running.\nEye tracking is active. Camera is running for continuous cursor control."))
            .build()
        
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
