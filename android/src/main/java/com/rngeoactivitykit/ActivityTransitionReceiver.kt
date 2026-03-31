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
            
            // We track the final, conclusive state of the batch to prevent rapid toggling
            var finalIsMoving: Boolean? = null
            var finalActivityStr = "UNKNOWN"
            var finalTransitionStr = "UNKNOWN"

            for (event in result.transitionEvents) {
                val activityTypeStr = toActivityString(event.activityType)
                val transitionTypeStr = toTransitionString(event.transitionType)
                
                Log.d("ActivityReceiver", "🏃 Motion Event: $activityTypeStr ($transitionTypeStr)")

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
                        DetectedActivity.STILL -> {
                            finalIsMoving = false
                            finalActivityStr = activityTypeStr
                            finalTransitionStr = transitionTypeStr
                        }
                    }
                } 
                // THE SAMSUNG SAFETY NET: 
                // If the phone explicitly exits a moving state, we assume it is now STILL.
                // This saves us if the OS forgets to send the "ENTER STILL" event.
                else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                    when (event.activityType) {
                        DetectedActivity.WALKING,
                        DetectedActivity.IN_VEHICLE,
                        DetectedActivity.ON_BICYCLE,
                        DetectedActivity.RUNNING -> {
                            // Only set to false if it hasn't already been set to true by a new ENTER event in this same batch
                            if (finalIsMoving != true) {
                                finalIsMoving = false
                                finalActivityStr = "STILL" // Force JS to see this as a STILL state
                                finalTransitionStr = "ENTER" // Fake the transition so JS processes it normally
                            }
                        }
                    }
                }
            }

            // Only update GPS hardware and JS Bridge if we found a conclusive state
            if (finalIsMoving != null) {
                try {
                    if (finalIsMoving) {
                         // User is moving: Speed up to 30 seconds
                         LocationHelper.shared?.setLocationUpdateInterval(30000)
                    } else {
                         // User stopped: Slow down to 5 minutes
                         LocationHelper.shared?.setLocationUpdateInterval(300000)
                    }
                } catch (e: Exception) {
                    Log.e("ActivityReceiver", "Failed to update location interval: ${e.message}")
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