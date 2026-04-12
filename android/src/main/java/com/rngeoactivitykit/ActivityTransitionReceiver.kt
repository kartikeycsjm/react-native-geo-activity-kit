package com.rngeoactivitykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
            
            var finalIsMoving: Boolean? = null
            var finalActivityStr = "UNKNOWN"
            var finalTransitionStr = "UNKNOWN"

            val nowNanos = SystemClock.elapsedRealtimeNanos()

            Log.w("ActivityReceiver", "==================================================")
            Log.w("ActivityReceiver", "📦 BATCH RECEIVED | Contains ${result.transitionEvents.size} events")

            for (event in result.transitionEvents) {
                val activityTypeStr = toActivityString(event.activityType)
                val transitionTypeStr = toTransitionString(event.transitionType)
                
                val ageSeconds = (nowNanos - event.elapsedRealTimeNanos) / 1_000_000_000L
                
                Log.d("ActivityReceiver", "   🏃 Event: $activityTypeStr ($transitionTypeStr) | Happened ${ageSeconds}s ago")

                // ✅ The 2-Hour Filter (7200s) - Allows Samsung batches but blocks cold-boot glitches
                if (ageSeconds > 7200) {
                    Log.e("ActivityReceiver", "   ❌ DROPPED: Older than 2 hours (Age: ${ageSeconds}s) - Cold Boot Glitch")
                    continue
                } else {
                    Log.i("ActivityReceiver", "   ✅ ACCEPTED: Processed successfully")
                }

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
                else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                    when (event.activityType) {
                        DetectedActivity.WALKING,
                        DetectedActivity.IN_VEHICLE,
                        DetectedActivity.ON_BICYCLE,
                        DetectedActivity.RUNNING -> {
                            finalIsMoving = false
                            finalActivityStr = "STILL"
                            finalTransitionStr = "ENTER" 
                        }
                    }
                }
            }

            if (finalIsMoving != null) {
                Log.w("ActivityReceiver", "🎯 FINAL STATE DECIDED: $finalActivityStr (Moving: $finalIsMoving)")
                
                try {
                    if (finalIsMoving) {
                         // Hardware says moving
                         LocationHelper.shared?.assumedMotionState = true
                         LocationHelper.shared?.setLocationUpdateInterval(30000)
                         LocationHelper.shared?.requestSingleUpdate()
                    } else {
                         // Hardware says stopped
                         LocationHelper.shared?.assumedMotionState = false
                         LocationHelper.shared?.setLocationUpdateInterval(180000)
                    }
                } catch (e: Exception) {
                    Log.e("ActivityReceiver", "Failed to update location interval: ${e.message}")
                }

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
            } else {
                Log.w("ActivityReceiver", "⚠️ NO FINAL STATE DECIDED (All events were dropped)")
            }
            Log.w("ActivityReceiver", "==================================================")
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