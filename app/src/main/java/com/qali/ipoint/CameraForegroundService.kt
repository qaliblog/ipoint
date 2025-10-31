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
            acquire()
        }
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure we're still in foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Renew wake lock if needed
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps camera active for eye tracking"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
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
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iPoint Eye Tracking Active")
            .setContentText("Camera is running for eye tracking")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Wake lock icon alternative
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .build()
    }
    
    fun updateNotification(text: String? = null) {
        val notification = createNotification().apply {
            if (text != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationCompat.Builder(this@CameraForegroundService, CHANNEL_ID)
                    .setContentText(text)
            }
        }
        notificationManager?.notify(NOTIFICATION_ID, createNotification().apply {
            // Update with custom text if provided
            if (text != null) {
                NotificationCompat.Builder(this@CameraForegroundService, CHANNEL_ID)
                    .setContentTitle("iPoint Eye Tracking Active")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                    .also { notificationManager?.notify(NOTIFICATION_ID, it) }
            }
        })
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
