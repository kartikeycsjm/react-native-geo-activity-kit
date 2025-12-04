package com.vaamanhr.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.sqrt

class SensorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), SensorEventListener {

    private val sensorManager: SensorManager = reactContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    // --- CONFIGURATION: Default to 30s ---
    @Volatile private var locationInterval: Long = 30000 
    private var isLocationClientRunning: Boolean = false

    private val gravity = floatArrayOf(0f, 0f, 0f)
    private val linearAcceleration = floatArrayOf(0f, 0f, 0f)
    private val alpha: Float = 0.8f 
    
    @Volatile private var motionThreshold: Float = 0.8f
    @Volatile private var currentState: String = "STATIONARY"
    private var isMotionDetectorStarted: Boolean = false
    @Volatile private var potentialState: String = "STATIONARY"
    @Volatile private var consecutiveCount = 0

    @Volatile private var startStabilityThreshold: Int = 20
    @Volatile private var stopStabilityThreshold: Int = 3000
    @Volatile private var samplingPeriodUs: Int = 100_000 

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val notificationManager: NotificationManager =
        reactContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val GEOFENCE_CHANNEL_ID = "geofence-channel-id"
    private val GEOFENCE_OUT_ID = 101 
    private val GEOFENCE_IN_ID = 102 
    
    private val appIcon: Int = reactApplicationContext.applicationInfo.icon.let {
        if (it != 0) it else android.R.drawable.ic_dialog_info 
    }

    init {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(reactContext)
        
        locationRequest = LocationRequest.create().apply {
            interval = locationInterval
            fastestInterval = locationInterval
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation ?: return
                
                val location = locationResult.lastLocation!!
                val params = Arguments.createMap()
                params.putDouble("latitude", location.latitude)
                params.putDouble("longitude", location.longitude)
                params.putString("timestamp", isoFormatter.format(Date(location.time)))
                params.putDouble("accuracy", location.accuracy.toDouble())
                
                emitEvent(reactApplicationContext, "onLocationLog", params)
            }
        }
        
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

    override fun getName(): String = "RNSensorModule"

    private fun updateLocationRequest(priority: Int, intervalMs: Long) {
        if (locationRequest.priority == priority && locationRequest.interval == intervalMs) return

        Log.i("SensorModule", "Switching Location Mode -> Priority: $priority, Interval: $intervalMs")

        locationRequest = LocationRequest.create().apply {
            this.interval = intervalMs
            this.fastestInterval = intervalMs
            this.priority = priority
        }
        
        if (isLocationClientRunning) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e("SensorModule", "Error applying new location request: ${e.message}")
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isMotionDetectorStarted) {
            
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

            linearAcceleration[0] = event.values[0] - gravity[0]
            linearAcceleration[1] = event.values[1] - gravity[1]
            linearAcceleration[2] = event.values[2] - gravity[2]

            val magnitude = sqrt(
                (linearAcceleration[0] * linearAcceleration[0] +
                 linearAcceleration[1] * linearAcceleration[1] +
                 linearAcceleration[2] * linearAcceleration[2]).toDouble()
            ).toFloat()

            val newState = if (magnitude > motionThreshold) "MOVING" else "STATIONARY"

            if (newState == potentialState) {
                consecutiveCount++
            } else {
                potentialState = newState
                consecutiveCount = 1
            }

            var stabilityMet = false
            if (potentialState == "MOVING" && consecutiveCount >= startStabilityThreshold) {
                stabilityMet = true
            } else if (potentialState == "STATIONARY" && consecutiveCount >= stopStabilityThreshold) {
                stabilityMet = true
            }

            if (stabilityMet && potentialState != currentState) {
                currentState = potentialState
                
                if (currentState == "MOVING") {
                    // Moving: High Accuracy GPS. 
                    updateLocationRequest(Priority.PRIORITY_HIGH_ACCURACY, locationInterval)
                    startLocationUpdates()
                } else {
                    // Stationary: Balanced Power (Cell/Wifi). 3-minute heartbeat.
                    updateLocationRequest(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180000)
                    startLocationUpdates()
                }

                val params = Arguments.createMap()
                params.putString("state", currentState)
                emitEvent(reactApplicationContext, "onMotionStateChanged", params)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- CLEANUP FIX: Prevents "Zombie" listeners on hot-reload ---
    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        try {
            // Stop Sensors
            sensorManager.unregisterListener(this)
            
            // Stop Location Updates
            if (isLocationClientRunning) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isLocationClientRunning = false
            }
            Log.i("SensorModule", "Cleaned up sensors and location updates.")
        } catch (e: Exception) {
            Log.e("SensorModule", "Error during cleanup: ${e.message}")
        }
    }

    @ReactMethod
    fun startMotionDetector(threshold: Double, promise: Promise) {
        if (accelerometer == null) {
            promise.reject("NO_SENSOR", "Accelerometer not available on this device")
            return
        }
        motionThreshold = threshold.toFloat()
        isMotionDetectorStarted = true
        currentState = "STATIONARY" 
        potentialState = "STATIONARY"
        consecutiveCount = 0
        
        sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
        
        updateLocationRequest(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180000)
        startLocationUpdates()
        
        promise.resolve(true)
    }

    @ReactMethod
    fun stopMotionDetector(promise: Promise) {
        isMotionDetectorStarted = false
        sensorManager.unregisterListener(this, accelerometer)
        // Explicit stop from JS (End Shift)
        stopLocationUpdates()
        promise.resolve(true)
    }

    @ReactMethod
    fun setLocationUpdateInterval(interval: Double, promise: Promise) {
        locationInterval = interval.toLong()
        if (currentState == "MOVING" && isLocationClientRunning) {
             updateLocationRequest(Priority.PRIORITY_HIGH_ACCURACY, locationInterval)
        }
        promise.resolve(true)
    }
    
    // ... Keep all other methods (fireGeofenceAlert, isAvailable, etc.) exactly as before ...
    
    // BOILERPLATE BELOW (Shortened for brevity, but you keep it in your file)
    private fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        return finePermission == PackageManager.PERMISSION_GRANTED || coarsePermission == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isLocationClientRunning) return
        if (!hasLocationPermission()) {
            val params = Arguments.createMap()
            params.putString("error", "LOCATION_PERMISSION_DENIED")
            params.putString("message", "Location permission is not granted.")
            emitEvent(reactApplicationContext, "onLocationError", params)
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            isLocationClientRunning = true
            Log.i("SensorModule", "Location updates started.")
        } catch (e: Exception) {
            val params = Arguments.createMap()
            params.putString("error", "START_LOCATION_FAILED")
            params.putString("message", "Error starting location: ${e.message}")
            emitEvent(reactApplicationContext, "onLocationError", params)
        }
    }

    private fun stopLocationUpdates() {
        if (!isLocationClientRunning) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationClientRunning = false
            Log.i("SensorModule", "Location updates stopped.")
        } catch (e: Exception) {
            Log.e("SensorModule", "Failed to stop location updates: " + e.message)
        }
    }

    private fun emitEvent(reactContext: ReactContext, eventName: String, params: WritableMap?) {
       if (reactContext.hasActiveCatalystInstance()) {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
        }
    }

    private fun createPendingIntent(requestCode: Int): PendingIntent? {
        val launchIntent = reactApplicationContext.packageManager.getLaunchIntentForPackage(reactApplicationContext.packageName)
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        return launchIntent?.let { PendingIntent.getActivity(reactApplicationContext, requestCode, it, pendingIntentFlag) }
    }

    @ReactMethod
    fun setStabilityThresholds(startThreshold: Int, stopThreshold: Int, promise: Promise) {
        try {
            startStabilityThreshold = startThreshold.coerceAtLeast(1)
            stopStabilityThreshold = stopThreshold.coerceAtLeast(1)
            promise.resolve(true)
        } catch (e: Exception) { promise.reject("CONFIG_ERROR", "Failed: ${e.message}") }
    }

    @ReactMethod
    fun setUpdateInterval(ms: Int, promise: Promise) {
        samplingPeriodUs = ms.coerceAtLeast(100) * 1000 
        if (isMotionDetectorStarted) {
            sensorManager.unregisterListener(this, accelerometer)
            sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
        }
        promise.resolve(true)
    }
    
    @ReactMethod
    fun isAvailable(promise: Promise) {
        val map = Arguments.createMap()
        map.putBoolean("accelerometer", accelerometer != null)
        map.putBoolean("gyroscope", false) 
        promise.resolve(map)
    }

    @ReactMethod
    fun fireGeofenceAlert(type: String, userName: String, promise: Promise) {
        try {
            val pendingIntent = createPendingIntent(0) 
            if (type == "OUT") {
                val notification = NotificationCompat.Builder(reactApplicationContext, GEOFENCE_CHANNEL_ID)
                    .setSmallIcon(appIcon).setContentTitle("Geofence Alert 🔔")
                    .setContentText("$userName, you seem to have moved out of your designated work area.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(pendingIntent).setOngoing(true).setAutoCancel(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
                notificationManager.notify(GEOFENCE_OUT_ID, notification)
            } else if (type == "IN") {
                notificationManager.cancel(GEOFENCE_OUT_ID)
                val notification = NotificationCompat.Builder(reactApplicationContext, GEOFENCE_CHANNEL_ID)
                    .setSmallIcon(appIcon).setContentTitle("You are in again ✅")
                    .setContentText("$userName, you have moved back into your designated work area.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT).setContentIntent(pendingIntent)
                    .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
                notificationManager.notify(GEOFENCE_IN_ID, notification)
            }
            promise.resolve(true)
        } catch (e: Exception) { promise.reject("NOTIFY_FAILED", e.message) }
    }
    
    @ReactMethod
    fun fireGenericAlert(title: String, body: String, id: Int, promise: Promise) {
        try {
            val pendingIntent = createPendingIntent(id) 
            val notification = NotificationCompat.Builder(reactApplicationContext, GEOFENCE_CHANNEL_ID)
                .setSmallIcon(appIcon).setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
            notificationManager.notify(id, notification)
            promise.resolve(true)
        } catch (e: Exception) { promise.reject("NOTIFY_FAILED", e.message) }
    }

    @ReactMethod
    fun cancelGenericAlert(id: Int, promise: Promise) {
        try { notificationManager.cancel(id); promise.resolve(true) } catch (e: Exception) { promise.reject("CANCEL_FAILED", e.message) }
    }

    @ReactMethod fun addListener(eventName: String) {}
    @ReactMethod fun removeListeners(count: Int) {}
}