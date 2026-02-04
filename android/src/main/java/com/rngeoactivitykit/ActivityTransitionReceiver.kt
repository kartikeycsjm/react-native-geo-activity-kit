package com.rngeoactivitykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            
            for (event in result.transitionEvents) {
                val activityTypeStr = toActivityString(event.activityType)
                val transitionTypeStr = toTransitionString(event.transitionType)
                
                Log.d("ActivityReceiver", "🏃 Motion Event: $activityTypeStr ($transitionTypeStr)")

                // PROD GRADE LOGIC:
                // We are "Moving" if we ENTER a moving state OR if we EXIT the Still state.
                // Exiting "Still" is the fastest way to detect movement start.
                val isMoving = (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER && 
                               (event.activityType == DetectedActivity.WALKING || 
                                event.activityType == DetectedActivity.IN_VEHICLE || 
                                event.activityType == DetectedActivity.ON_BICYCLE || 
                                event.activityType == DetectedActivity.RUNNING)) ||
                               (event.activityType == DetectedActivity.STILL && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT)

                try {
                    if (isMoving) {
                         // User is moving: Speed up to 30 seconds
                         LocationHelper.shared?.setLocationUpdateInterval(30000)
                    } else if (event.activityType == DetectedActivity.STILL && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                         // User stopped: Slow down to 5 minutes
                         LocationHelper.shared?.setLocationUpdateInterval(300000)
                    }
                } catch (e: Exception) {
                    Log.e("ActivityReceiver", "Failed to update location interval directly: ${e.message}")
                }

                // Send to JS
                try {
                    val reactContext = context.applicationContext as? ReactApplicationContext
                        ?: TrackingService.instance?.application as? ReactApplicationContext

                    if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
                        val params = Arguments.createMap()
                        params.putString("activity", activityTypeStr)
                        params.putString("transition", transitionTypeStr)
                        params.putBoolean("isMoving", isMoving)
                        params.putString("state", if (isMoving) "MOVING" else "STATIONARY")
                        
                        reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                            .emit("onMotionStateChanged", params)
                    }
                } catch (e: Exception) {
                    Log.e("ActivityReceiver", "JS Bridge Error: ${e.message}")
                }
            }
        }
    }

    private fun toActivityString(type: Int): String {
        return when (type) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.TILTING -> "TILTING"
            else -> "UNKNOWN"
        }
    }

    private fun toTransitionString(type: Int): String {
        return when (type) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
            else -> "UNKNOWN"
        }
    }
}