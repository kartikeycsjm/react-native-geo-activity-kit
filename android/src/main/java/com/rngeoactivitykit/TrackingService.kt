package com.rngeoactivitykit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class TrackingService : Service() {

    companion object {
        var instance: TrackingService? = null
        const val NOTIFICATION_ID = 9991
        const val CHANNEL_ID = "geo_activity_kit_channel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE = "ACTION_UPDATE"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("TrackingService", "✅ Service Created")
        createNotificationChannel()
        
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoKit::TrackingLock")
            wakeLock?.setReferenceCounted(false) // PROD: Ensure we don't over-release
            wakeLock?.acquire()
            Log.d("TrackingService", "🔒 WakeLock Acquired (Permanent)")
        } catch (e: Exception) {
            Log.e("TrackingService", "❌ Failed to acquire WakeLock: ${e.message}")
        }
        
        // Start Location Updates on Create via Helper
        LocationHelper.shared?.startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        
        when (intent.action) {
            ACTION_START -> {
                Log.d("TrackingService", "➡️ Action: START")
                val title = intent.getStringExtra("title") ?: "Location Active"
                val body = intent.getStringExtra("body") ?: "Monitoring in background..."
                startForegroundService(title, body)
                
                // Ensure sensors are running
                LocationHelper.shared?.startLocationUpdates()
            }
            ACTION_STOP -> {
                Log.d("TrackingService", "⏹️ Action: STOP")
                stopForegroundService()
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra("title")
                val body = intent.getStringExtra("body")
                updateNotification(title, body)
            }
        }
        return START_STICKY
    }

    private fun startForegroundService(title: String, body: String) {
        val notification = buildNotification(title, body)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("TrackingService", "Error starting foreground: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            LocationHelper.shared?.stopLocationUpdates()
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification(title: String?, body: String?) {
        val notification = buildNotification(title ?: "Location Active", body ?: "Monitoring...")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, body: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this, 0, launchIntent, 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val appIcon = applicationInfo.icon.let { if (it != 0) it else android.R.drawable.ic_menu_myplaces }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(appIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TrackingService", "❌ Service Destroyed")
        instance = null
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
    }
}