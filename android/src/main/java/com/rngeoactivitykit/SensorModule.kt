package com.rngeoactivitykit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.app.Notification
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import java.util.Date
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlin.math.sqrt

class SensorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), SensorEventListener {
    private val sensorManager: SensorManager = reactContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    @Volatile private var locationInterval: Long = 90000 
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
        
        updateLocationRequest()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation ?: return
                
                val location = locationResult.lastLocation!!
                val params = Arguments.createMap()
                params.putDouble("latitude", location.latitude)
                params.putDouble("longitude", location.longitude)
                params.putString("timestamp", isoFormatter.format(Date(location.time)))
                
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

    private fun updateLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = locationInterval
            fastestInterval = locationInterval
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    
    override fun getName(): String = "RNSensorModule"


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
                    startLocationUpdates()
                } else {
                    stopLocationUpdates()
                }
                val params = Arguments.createMap()
                params.putString("state", currentState)
                emitEvent(reactApplicationContext, "onMotionStateChanged", params)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarsePermission = ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return finePermission == PackageManager.PERMISSION_GRANTED || coarsePermission == PackageManager.PERMISSION_GRANTED
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
        
        promise.resolve(true)
    }

    @ReactMethod
    fun stopMotionDetector(promise: Promise) {
        isMotionDetectorStarted = false
        sensorManager.unregisterListener(this, accelerometer)
        stopLocationUpdates()
        
        promise.resolve(true)
    }

    @ReactMethod
    fun setLocationUpdateInterval(interval: Double, promise: Promise) {
        locationInterval = interval.toLong()
        updateLocationRequest()
        
        if (isLocationClientRunning) {
            stopLocationUpdates()
            startLocationUpdates()
        }
        promise.resolve(true)
    }
    
    @ReactMethod
    fun setStabilityThresholds(startThreshold: Int, stopThreshold: Int, promise: Promise) {
        try {
            startStabilityThreshold = startThreshold.coerceAtLeast(1)
            stopStabilityThreshold = stopThreshold.coerceAtLeast(1)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to set stability thresholds: ${e.message}")
        }
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
    

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isLocationClientRunning) {
            return
        }
        if (!hasLocationPermission()) {
            val params = Arguments.createMap()
            params.putString("error", "LOCATION_PERMISSION_DENIED")
            params.putString("message", "Location permission is not granted. Please request permission from the user.")
            emitEvent(reactApplicationContext, "onLocationError", params)
            Log.w("SensorModule", "Location permission denied.")
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isLocationClientRunning = true
            Log.i("SensorModule", "Location updates started.")
        } catch (e: Exception) {
            Log.e("SensorModule", "Failed to start location updates: " + e.message)
            val params = Arguments.createMap()
            params.putString("error", "START_LOCATION_FAILED")
            params.putString("message", "An unexpected error occurred while starting location updates: ${e.message}")
            emitEvent(reactApplicationContext, "onLocationError", params)
        }
    }

    private fun stopLocationUpdates() {
        if (!isLocationClientRunning) {
            return
        }
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
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        } else {
            Log.w("SensorModule", "Catalyst instance not active for event $eventName")
        }
    }


    private fun createPendingIntent(requestCode: Int): PendingIntent? {
        val launchIntent = reactApplicationContext.packageManager.getLaunchIntentForPackage(reactApplicationContext.packageName)
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        return launchIntent?.let {
            PendingIntent.getActivity(
                reactApplicationContext,
                requestCode,
                it,
                pendingIntentFlag
            )
        }
    }


    @ReactMethod
    fun fireGeofenceAlert(type: String, userName: String, promise: Promise) {
        try {
            val pendingIntent = createPendingIntent(0) 

            if (type == "OUT") {
                val notification = NotificationCompat.Builder(reactApplicationContext, GEOFENCE_CHANNEL_ID)
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

                
                val notification = NotificationCompat.Builder(reactApplicationContext, GEOFENCE_CHANNEL_ID)
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
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("SensorModule", "Failed to fire notification: " + e.message)
            promise.reject("NOTIFY_FAILED", e.message)
        }
    }
    
    @ReactMethod
    fun fireGenericAlert(title: String, body: String, id: Int, promise: Promise) {
        try {
            val pendingIntent = createPendingIntent(id) 

            val notification = NotificationCompat.Builder(reactApplicationContext, GEOFENCE_CHANNEL_ID)
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
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("SensorModule", "Failed to fire generic notification: " + e.message)
            promise.reject("NOTIFY_FAILED", e.message)
        }
    }


    @ReactMethod
    fun cancelGenericAlert(id: Int, promise: Promise) {
        try {
            notificationManager.cancel(id)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("SensorModule", "Failed to cancel generic notification: " + e.message)
            promise.reject("CANCEL_FAILED", e.message)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {}
    @ReactMethod
    fun removeListeners(count: Int) {}

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        Log.w("SensorModule", "onCatalystInstanceDestroy called, but sensors are being kept alive.")
    }
}