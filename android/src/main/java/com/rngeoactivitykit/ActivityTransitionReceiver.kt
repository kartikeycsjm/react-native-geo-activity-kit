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
            
            // Track the *final* conclusive state in this batched update
            var finalIsMoving: Boolean? = null
            var finalActivityStr = "UNKNOWN"
            var finalTransitionStr = "UNKNOWN"

            for (event in result.transitionEvents) {
                val activityTypeStr = toActivityString(event.activityType)
                val transitionTypeStr = toTransitionString(event.transitionType)
                
                Log.d("ActivityReceiver", "🏃 Motion Event: $activityTypeStr ($transitionTypeStr)")

                // PROD GRADE LOGIC:
                // Only mark as moving if explicitly ENTERING a motion state.
                // We completely ignore EXIT STILL to prevent "Ghost Steps" from table vibrations.
                if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    when (event.activityType) {
                        DetectedActivity.WALKING,
                        DetectedActivity.IN_VEHICLE,
                        DetectedActivity.ON_BICYCLE,
                        DetectedActivity.RUNNING -> {
                            finalIsMoving = true
                            finalActivityStr = activityTypeStr
                            finalTransitionStr = transitionTypeStr
                        }
                        DetectedActivity.STILL,
                        DetectedActivity.TILTING -> {
                            finalIsMoving = false
                            finalActivityStr = activityTypeStr
                            finalTransitionStr = transitionTypeStr
                        }
                    }
                }
            }

            // Only update GPS hardware and JS Bridge if we found a conclusive ENTER state in this batch
            if (finalIsMoving != null) {
                try {
                    if (finalIsMoving) {
                         // User explicitly started moving: Speed up to 30 seconds
                         LocationHelper.shared?.setLocationUpdateInterval(30000)
                    } else {
                         // User explicitly stopped or is tilting: Slow down to 5 minutes
                         LocationHelper.shared?.setLocationUpdateInterval(300000)
                    }
                } catch (e: Exception) {
                    Log.e("ActivityReceiver", "Failed to update location interval directly: ${e.message}")
                }

                // Send the definitive state to React Native JS
                try {
                    val reactContext = ReactContextHolder.get()
                        ?: (context.applicationContext as? ReactApplicationContext)
                        ?: TrackingService.instance?.application as? ReactApplicationContext

                    if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
                        val params = Arguments.createMap()
                        params.putString("activity", finalActivityStr)
                        params.putString("transition", finalTransitionStr)
                        params.putBoolean("isMoving", finalIsMoving)
                        params.putString("state", if (finalIsMoving) "MOVING" else "STATIONARY")
                        
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