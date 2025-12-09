package com.rngeoactivitykit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.ReactApplicationContext

class NotificationHelper(private val context: ReactApplicationContext) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val GEOFENCE_CHANNEL_ID = "geofence-channel-id"
        const val GEOFENCE_OUT_ID = 101
        const val GEOFENCE_IN_ID = 102
    }

    private val appIcon: Int = context.applicationInfo.icon.let {
        if (it != 0) it else android.R.drawable.ic_dialog_info
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Geofence Alerts"
            val descriptionText = "Notifications for geofence and work reminders."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(GEOFENCE_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(300, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun fireGeofenceAlert(type: String, userName: String) {
        val pendingIntent = createPendingIntent(0)
        
        if (type == "OUT") {
            val notification = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
                .setSmallIcon(appIcon)
                .setContentTitle("Geofence Alert 🔔")
                .setContentText("$userName, you seem to have moved out of your designated work area.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            notificationManager.notify(GEOFENCE_OUT_ID, notification)
        } else if (type == "IN") {
            notificationManager.cancel(GEOFENCE_OUT_ID)
            val notification = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
                .setSmallIcon(appIcon)
                .setContentTitle("You are in again ✅")
                .setContentText("$userName, you have moved back into your designated work area.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            notificationManager.notify(GEOFENCE_IN_ID, notification)
        }
    }

    fun fireGenericAlert(title: String, body: String, id: Int) {
        val pendingIntent = createPendingIntent(id)
        val notification = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(appIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify(id, notification)
    }

    fun cancelGenericAlert(id: Int) {
        notificationManager.cancel(id)
    }

    private fun createPendingIntent(requestCode: Int): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        return launchIntent?.let { PendingIntent.getActivity(context, requestCode, it, pendingIntentFlag) }
    }
}