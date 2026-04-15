package com.rngeoactivitykit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class LocationHelper(
    private val appContext: Context,
    private var reactContext: com.facebook.react.bridge.ReactApplicationContext? = null
) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var shared: LocationHelper? = null
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private var locationCallback: LocationCallback
    private var locationRequest: LocationRequest
    
    var isLocationClientRunning: Boolean = false
        private set

    // 🚨 STATE VARIABLES FOR MATH OVERRIDE 🚨
    var assumedMotionState: Boolean = false
    var hasResolvedUnknownState: Boolean = false 
    private var lastAccurateLocation: android.location.Location? = null
    private var consecutiveStillPings = 0

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        shared = this
        
        // PROD GRADE: Match JS STATIONARY_INTERVAL (3 Minutes)
        val defaultInterval = 180000L 

        locationRequest = LocationRequest.create().apply {
            interval = defaultInterval
            fastestInterval = defaultInterval
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { processLocation(it) }
            }
        }
    }

    // 🚨 EXTRACTED PROCESSING LOGIC 🚨
    private fun processLocation(location: android.location.Location) {
        // Filter noise. If accuracy is very bad (>200m), ignore it entirely.
        if (location.accuracy > 200) return

        Log.d("LocationHelper", "📍 New Location: ${location.latitude}, ${location.longitude} (Acc: ${location.accuracy}m)")

        // Raised threshold to < 50m to allow indoor math override
        if (location.accuracy < 50) {
            if (lastAccurateLocation != null) {
                val distance = location.distanceTo(lastAccurateLocation!!) 
                
                // Doppler speed check: > 1.0 m/s is instant proof of walking
                val isMovingBySpeed = location.hasSpeed() && location.speed > 1.0f

                // ✅ NEW RULE: Resolve the initial UNKNOWN state using math
                if (!hasResolvedUnknownState && distance < 10) {
                    consecutiveStillPings++
                    if (consecutiveStillPings >= 2) {
                        Log.w("LocationHelper", "🚨 OVERRIDE: Resolving UNKNOWN. Math proves user is STILL.")
                        hasResolvedUnknownState = true // Lock this rule out for the rest of the shift
                        assumedMotionState = false
                        consecutiveStillPings = 0
                        setLocationUpdateInterval(180000) 
                        
                        val params = Arguments.createMap()
                        params.putString("activity", "STILL")
                        params.putString("transition", "ENTER")
                        params.putBoolean("isMoving", false)
                        params.putString("state", "STATIONARY")
                        sendEvent("onMotionStateChanged", params)
                    }
                } 
                // --- Moto Fix: Hardware is asleep, but Math proves motion ---
                else if (!assumedMotionState && (distance > 30 || isMovingBySpeed)) {
                    Log.w("LocationHelper", "🚨 OVERRIDE: Sensor asleep, but motion detected. Forcing WALKING.")
                    hasResolvedUnknownState = true // Walking also resolves the boot state
                    assumedMotionState = true
                    consecutiveStillPings = 0
                    setLocationUpdateInterval(30000) 
                    
                    val params = Arguments.createMap()
                    params.putString("activity", "WALKING")
                    params.putString("transition", "ENTER")
                    params.putBoolean("isMoving", true)
                    params.putString("state", "MOVING")
                    sendEvent("onMotionStateChanged", params)

                } 
                // --- Samsung Fix: Hardware stuck in WALKING, but Math proves they haven't moved ---
                else if (assumedMotionState && distance < 10) {
                    consecutiveStillPings++
                    if (consecutiveStillPings >= 2) { 
                        Log.w("LocationHelper", "🚨 OVERRIDE: Sensor stuck WALKING, but stationary. Forcing STILL.")
                        assumedMotionState = false
                        consecutiveStillPings = 0
                        setLocationUpdateInterval(180000) 
                        
                        val params = Arguments.createMap()
                        params.putString("activity", "STILL")
                        params.putString("transition", "ENTER")
                        params.putBoolean("isMoving", false)
                        params.putString("state", "STATIONARY")
                        sendEvent("onMotionStateChanged", params)
                    }
                } 
                // --- They are still actively moving, reset the stillness counter ---
                else if (assumedMotionState && distance >= 10) {
                    consecutiveStillPings = 0
                }
            }
            lastAccurateLocation = location
        }

        var isMock = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock = location.isMock
        } else {
            isMock = location.isFromMockProvider
        }

        withWakeLock {
            val params = Arguments.createMap()
            params.putDouble("latitude", location.latitude)
            params.putDouble("longitude", location.longitude)
            params.putString("timestamp", isoFormatter.format(Date(location.time)))
            params.putDouble("accuracy", location.accuracy.toDouble())
            params.putBoolean("is_mock", isMock)

            sendEvent("onLocationLog", params)
        }
    }

    fun attachReactContext(context: com.facebook.react.bridge.ReactApplicationContext) {
        reactContext = context
    }

    fun setLocationUpdateInterval(intervalMs: Long) {
        val newPriority = if (intervalMs < 60000) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        updateLocationRequest(newPriority, intervalMs)
    }

// LocationHelper.kt
    fun updateLocationRequest(priority: Int, intervalMs: Long) {
        // 🔥 FORCE HIGH_ACCURACY: It's the only way to pierce through pocket denim 
        // and ensure the OS doesn't defer your request during Doze.
        val forcedPriority = Priority.PRIORITY_HIGH_ACCURACY
    
        if (locationRequest.interval == intervalMs && locationRequest.priority == forcedPriority && isLocationClientRunning) {
            return
        }
    
        locationRequest = LocationRequest.create().apply {
            this.interval = intervalMs
            this.fastestInterval = 30000L // Allow other apps to "help" with faster pings
            this.priority = forcedPriority
            
            // 🔥 THE PRODUCTION FIX: maxWaitTime
            // This is the deadline. It tells Android: "You can batch or delay, 
            // but you MUST deliver location data to me at this 3-min mark".
            this.maxWaitTime = intervalMs 
        }
        
        if (isLocationClientRunning) {
            stopLocationUpdates()
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isLocationClientRunning) return
        if (!hasLocationPermission()) {
            Log.e("LocationHelper", "Permission Missing")
            emitLocationError("PERMISSION_MISSING", "Location permission missing")
            return
        }
        try {
            // Reset the state flags every time tracking starts fresh
            hasResolvedUnknownState = false 
            assumedMotionState = false
            consecutiveStillPings = 0
            lastAccurateLocation = null

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            isLocationClientRunning = true
            Log.d("LocationHelper", "✅ Location Updates STARTED.")
        } catch (e: Exception) {
            Log.e("LocationHelper", "Error starting location: ${e.message}")
            emitLocationError("START_LOCATION_FAILED", e.message ?: "Error starting location")
        }
    }

    fun stopLocationUpdates() {
        if (!isLocationClientRunning) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationClientRunning = false
            Log.d("LocationHelper", "🛑 Location Updates STOPPED.")
        } catch (e: Exception) {
            Log.e("LocationHelper", "Failed to stop: " + e.message)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun requestSingleUpdate() {
        if (!hasLocationPermission()) return
        Log.w("LocationHelper", "⚡ FORCING IMMEDIATE GPS PING (Motion State Changed)")
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                Log.d("LocationHelper", "⚡ IMMEDIATE PING SUCCESS: ${location?.latitude}, ${location?.longitude}")
                location?.let { processLocation(it) }
            }.addOnFailureListener { e ->
                Log.e("LocationHelper", "⚡ IMMEDIATE PING FAILED: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "Single update crashed: ${e.message}")
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        
        // We only require "While using the app" (Fine or Coarse) to run the Foreground Service
        return (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED)
    }

    private fun sendEvent(eventName: String, params: Any?) {
        try {
            val ctx = reactContext ?: ReactContextHolder.get()
            if (ctx != null && ctx.hasActiveCatalystInstance()) {
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "JS Error: ${e.message}")
        }
    }

    private fun emitLocationError(code: String, message: String) {
        val params = Arguments.createMap()
        params.putString("error", code)
        params.putString("message", message)
        sendEvent("onLocationError", params)
    }

    private fun withWakeLock(block: () -> Unit) {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GeoKit::LocationBurst"
        )
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(10_000)
        try {
            block()
        } finally {
            if (wakeLock.isHeld) {
                try { wakeLock.release() } catch (_: Exception) {}
            }
        }
    }
}