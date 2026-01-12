package com.rngeoactivitykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.location.Priority

class SensorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val notificationHelper = NotificationHelper(reactContext)
    private val locationHelper = LocationHelper(reactContext)
    
    private val motionDetector = MotionDetector(reactContext) { newState ->
        onMotionStateChanged(newState)
    }

    private var locationInterval: Long = 30000

    // --- NEW: Hardware GPS Toggle Receiver ---
    private val gpsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                
                val params = Arguments.createMap()
                params.putBoolean("enabled", isGpsEnabled)
                
                // Emit event to React Native
                sendEvent("onGpsStatusChanged", params)
            }
        }
    }

    override fun getName(): String = "RNSensorModule"

    private fun sendEvent(eventName: String, params: WritableMap) {
        if (reactApplicationContext.hasActiveCatalystInstance()) {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        }
    }

    private fun onMotionStateChanged(state: String) {
        if (state == "MOVING") {
            locationHelper.updateLocationRequest(Priority.PRIORITY_HIGH_ACCURACY, locationInterval)
            locationHelper.startLocationUpdates()
        } else {
            locationHelper.updateLocationRequest(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180000)
            locationHelper.startLocationUpdates()
        }
    }

    // --- NEW: Native Method to Start GPS Listener ---
    @ReactMethod
    fun registerGpsListener(promise: Promise) {
        try {
            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            reactApplicationContext.registerReceiver(gpsStatusReceiver, filter)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("REGISTER_FAILED", e.message)
        }
    }

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

    @ReactMethod
    fun startMotionDetector(threshold: Double, promise: Promise) {
        val success = motionDetector.start(threshold)
        if (!success) {
            promise.reject("NO_SENSOR", "Accelerometer not available")
            return
        }
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

    override fun onCatalystInstanceDestroy() {
        try {
            reactApplicationContext.unregisterReceiver(gpsStatusReceiver)
        } catch (e: Exception) {}
        
        super.onCatalystInstanceDestroy()
        motionDetector.stop()
        locationHelper.stopLocationUpdates()
        
        val intent = Intent(reactApplicationContext, TrackingService::class.java)
        intent.action = TrackingService.ACTION_STOP
        reactApplicationContext.startService(intent)
    }
}