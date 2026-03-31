package com.rngeoactivitykit

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

class MotionDetector(private val context: ReactApplicationContext) {

    private val activityClient = ActivityRecognition.getClient(context)
    private var pendingIntent: PendingIntent? = null

    private val transitions = listOf(
        // STILL
        ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
        ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
        
        // WALKING
        ActivityTransition.Builder().setActivityType(DetectedActivity.WALKING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
        ActivityTransition.Builder().setActivityType(DetectedActivity.WALKING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),

        // VEHICLE
        ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
        ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),

        // BICYCLE
        ActivityTransition.Builder().setActivityType(DetectedActivity.ON_BICYCLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
        ActivityTransition.Builder().setActivityType(DetectedActivity.ON_BICYCLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),

        // RUNNING
        ActivityTransition.Builder().setActivityType(DetectedActivity.RUNNING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
        ActivityTransition.Builder().setActivityType(DetectedActivity.RUNNING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),

        // TILTING (Prevents OS from dropping state chain when phone is picked up/put down)
        ActivityTransition.Builder().setActivityType(DetectedActivity.TILTING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
        ActivityTransition.Builder().setActivityType(DetectedActivity.TILTING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build()
    )

    @SuppressLint("MissingPermission")
    fun start(onSuccess: () -> Unit, onFailure: (Exception) -> Unit): Boolean {
        if (!hasPermission()) {
            return false
        }

        val request = ActivityTransitionRequest(transitions)
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        intent.action = "com.rngeoactivitykit.ACTION_PROCESS_ACTIVITY_TRANSITIONS"

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

        activityClient.requestActivityTransitionUpdates(request, pendingIntent!!)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onFailure(e)
            }

        return true
    }

    fun stop() {
        pendingIntent?.let {
            activityClient.removeActivityTransitionUpdates(it)
            pendingIntent = null
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required at runtime below Android 10
        }
    }
}
