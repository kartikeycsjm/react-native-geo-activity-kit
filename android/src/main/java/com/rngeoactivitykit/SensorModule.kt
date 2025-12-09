package com.rngeoactivitykit

import android.content.Intent
import android.os.Build
import com.facebook.react.bridge.*
import com.google.android.gms.location.Priority

class SensorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val notificationHelper = NotificationHelper(reactContext)
    private val locationHelper = LocationHelper(reactContext)
    
    // Connect Motion Logic to Location Logic
    private val motionDetector = MotionDetector(reactContext) { newState ->
        onMotionStateChanged(newState)
    }

    private var locationInterval: Long = 30000 

    override fun getName(): String = "RNSensorModule"

    // --- The "Smart Switch" Logic ---
    private fun onMotionStateChanged(state: String) {
        if (state == "MOVING") {
            // High Power
            locationHelper.updateLocationRequest(Priority.PRIORITY_HIGH_ACCURACY, locationInterval)
            locationHelper.startLocationUpdates()
        } else {
            // Low Power (Cell/Wifi) & Slow Updates
            locationHelper.updateLocationRequest(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180000)
            locationHelper.startLocationUpdates()
        }
    }

    // --- Service Methods ---
    @ReactMethod
    fun startForegroundService(title: String, body: String, promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, TrackingService::class.java)
            intent.action = TrackingService.ACTION_START
            intent.putExtra("title", title)
            intent.putExtra("body", body)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactApplicationContext.startForegroundService(intent)
            } else {
                reactApplicationContext.startService(intent)
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("START_FAILED", e.message)
        }
    }

    @ReactMethod
    fun stopForegroundService(promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, TrackingService::class.java)
            intent.action = TrackingService.ACTION_STOP
            reactApplicationContext.startService(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("STOP_FAILED", e.message)
        }
    }

    @ReactMethod
    fun updateServiceNotification(title: String, body: String, promise: Promise) {
        try {
            if (TrackingService.instance != null) {
                val intent = Intent(reactApplicationContext, TrackingService::class.java)
                intent.action = TrackingService.ACTION_UPDATE
                intent.putExtra("title", title)
                intent.putExtra("body", body)
                reactApplicationContext.startService(intent)
                promise.resolve(true)
            } else {
                promise.resolve(false)
            }
        } catch (e: Exception) {
            promise.reject("UPDATE_FAILED", e.message)
        }
    }

    // --- Sensor Methods ---
    @ReactMethod
    fun startMotionDetector(threshold: Double, promise: Promise) {
        val success = motionDetector.start(threshold)
        if (!success) {
            promise.reject("NO_SENSOR", "Accelerometer not available")
            return
        }
        
        // Start Location immediately (Balanced Mode)
        locationHelper.updateLocationRequest(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180000)
        locationHelper.startLocationUpdates()
        
        promise.resolve(true)
    }

    @ReactMethod
    fun stopMotionDetector(promise: Promise) {
        motionDetector.stop()
        locationHelper.stopLocationUpdates()
        promise.resolve(true)
    }

    @ReactMethod
    fun setLocationUpdateInterval(interval: Double, promise: Promise) {
        locationInterval = interval.toLong()
        promise.resolve(true)
    }

    @ReactMethod
    fun setStabilityThresholds(startThreshold: Int, stopThreshold: Int, promise: Promise) {
        motionDetector.startStabilityThreshold = startThreshold
        motionDetector.stopStabilityThreshold = stopThreshold
        promise.resolve(true)
    }

    @ReactMethod
    fun setUpdateInterval(ms: Int, promise: Promise) {
        motionDetector.setUpdateInterval(ms)
        promise.resolve(true)
    }

    @ReactMethod
    fun isAvailable(promise: Promise) {
        val map = Arguments.createMap()
        map.putBoolean("accelerometer", motionDetector.isSensorAvailable())
        map.putBoolean("gyroscope", false)
        promise.resolve(map)
    }

    @ReactMethod
    fun fireGeofenceAlert(type: String, userName: String, promise: Promise) {
        try {
            notificationHelper.fireGeofenceAlert(type, userName)
            promise.resolve(true)
        } catch (e: Exception) { promise.reject("NOTIFY_FAILED", e.message) }
    }

    @ReactMethod
    fun fireGenericAlert(title: String, body: String, id: Int, promise: Promise) {
        try {
            notificationHelper.fireGenericAlert(title, body, id)
            promise.resolve(true)
        } catch (e: Exception) { promise.reject("NOTIFY_FAILED", e.message) }
    }

    @ReactMethod
    fun cancelGenericAlert(id: Int, promise: Promise) {
        try {
            notificationHelper.cancelGenericAlert(id)
            promise.resolve(true)
        } catch (e: Exception) { promise.reject("CANCEL_FAILED", e.message) }
    }

    @ReactMethod fun addListener(eventName: String) {}
    @ReactMethod fun removeListeners(count: Int) {}

    // Cleanup
    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        motionDetector.stop()
        locationHelper.stopLocationUpdates()
        
        // Optional: Stop service if you want app death to kill service
        val intent = Intent(reactApplicationContext, TrackingService::class.java)
        intent.action = TrackingService.ACTION_STOP
        reactApplicationContext.startService(intent)
    }
}