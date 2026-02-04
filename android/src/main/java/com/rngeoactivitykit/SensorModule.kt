package com.rngeoactivitykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class SensorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val notificationHelper = NotificationHelper(reactContext)
    private val locationHelper = LocationHelper(reactContext)
    private val motionDetector = MotionDetector(reactContext)

    private val gpsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                try {
                    val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    
                    val params = Arguments.createMap()
                    params.putBoolean("enabled", isGpsEnabled)
                    
                    sendEvent("onGpsStatusChanged", params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun getName(): String {
        return "RNSensorModule"
    }

    init {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        reactContext.registerReceiver(gpsStatusReceiver, filter)
    }

    @ReactMethod
    fun startForegroundService(title: String, body: String, id: Int, promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, TrackingService::class.java)
            intent.action = TrackingService.ACTION_START
            intent.putExtra("title", title)
            intent.putExtra("body", body)
            // intent.putExtra("id", id) // Pass ID if you want to make the persistent notification dynamic later
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactApplicationContext.startForegroundService(intent)
            } else {
                reactApplicationContext.startService(intent)
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("START_SERVICE_FAILED", e.message)
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
            promise.reject("STOP_SERVICE_FAILED", e.message)
        }
    }

    @ReactMethod
    fun updateServiceNotification(title: String, body: String, promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, TrackingService::class.java)
            intent.action = TrackingService.ACTION_UPDATE
            intent.putExtra("title", title)
            intent.putExtra("body", body)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactApplicationContext.startForegroundService(intent)
            } else {
                reactApplicationContext.startService(intent)
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("UPDATE_FAILED", e.message)
        }
    }

    @ReactMethod
    fun startMotionDetector(threshold: Double, promise: Promise) {
        try {
            val success = motionDetector.start()
            if (success) {
                promise.resolve(true)
            } else {
                promise.reject("PERMISSION_DENIED", "ACTIVITY_RECOGNITION permission is required")
            }
        } catch (e: Exception) {
            promise.reject("START_MOTION_FAILED", e.message)
        }
    }

    @ReactMethod
    fun stopMotionDetector(promise: Promise) {
        try {
            motionDetector.stop()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("STOP_MOTION_FAILED", e.message)
        }
    }

    @ReactMethod
    fun setLocationUpdateInterval(intervalMs: Int) {
        try {
            locationHelper.setLocationUpdateInterval(intervalMs.toLong())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    @ReactMethod
    fun fireGeofenceAlert(type: String, userName: String, promise: Promise) {
        try {
            notificationHelper.fireGeofenceAlert(type, userName)
            promise.resolve(true)
        } catch (e: Exception) { 
            promise.reject("NOTIFY_FAILED", e.message) 
        }
    }

    @ReactMethod
    fun fireGenericAlert(title: String, body: String, id: Int, promise: Promise) {
        try {
            notificationHelper.fireGenericAlert(title, body, id)
            promise.resolve(true)
        } catch (e: Exception) { 
            promise.reject("NOTIFY_FAILED", e.message) 
        }
    }

    @ReactMethod
    fun cancelGenericAlert(id: Int, promise: Promise) {
        try {
            notificationHelper.cancelGenericAlert(id)
            promise.resolve(true)
        } catch (e: Exception) { 
            promise.reject("CANCEL_FAILED", e.message) 
        }
    }

    @ReactMethod
    fun registerGpsListener(promise: Promise) {
        try {
            val locationManager = reactApplicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            promise.reject("GPS_CHECK_FAILED", e.message)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}

    private fun sendEvent(eventName: String, params: Any?) {
        try {
            if (reactApplicationContext.hasActiveCatalystInstance()) {
                reactApplicationContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(eventName, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        try {
            reactApplicationContext.unregisterReceiver(gpsStatusReceiver)
            motionDetector.stop()
            locationHelper.stopLocationUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}