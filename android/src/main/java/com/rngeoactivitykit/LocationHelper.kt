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

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        shared = this
        
        // PROD GRADE: Start with 3 Minutes (Battery Saver)
        // We assume the user is stationary until the Motion Detector proves otherwise.
        val defaultInterval = 180000L // 3 Minutes

        locationRequest = LocationRequest.create().apply {
            interval = defaultInterval
            fastestInterval = defaultInterval
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation ?: return
                val location = locationResult.lastLocation!!
                
                // PROD GRADE: Filter noise. If accuracy is very bad (>200m), ignore it to save processing.
                if (location.accuracy > 200) return

                Log.d("LocationHelper", "📍 New Location: ${location.latitude}, ${location.longitude} (Acc: ${location.accuracy}m)")

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
        }
    }

    fun attachReactContext(context: com.facebook.react.bridge.ReactApplicationContext) {
        reactContext = context
    }

    fun setLocationUpdateInterval(intervalMs: Long) {
        val newPriority = if (intervalMs < 60000) {
            // High accuracy for intervals < 1 min
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            // Balanced power for stationary/slow updates
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        updateLocationRequest(newPriority, intervalMs)
    }

    fun updateLocationRequest(priority: Int, intervalMs: Long) {
        // PROD GRADE: Prevent restarting the hardware if nothing changed
        if (locationRequest.interval == intervalMs && locationRequest.priority == priority && isLocationClientRunning) {
            return
        }

        Log.d("LocationHelper", "🔄 Updating Request: Priority=$priority, Interval=${intervalMs}ms")

        locationRequest = LocationRequest.create().apply {
            this.interval = intervalMs
            this.fastestInterval = intervalMs
            this.priority = priority
        }
        
        if (isLocationClientRunning) {
            stopLocationUpdates()
            startLocationUpdates()
        } else {
            // Do not auto-start if it wasn't running. Wait for explicit start.
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        return (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) &&
            background == PackageManager.PERMISSION_GRANTED
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
        // Auto-release after 10 seconds as a safety net.
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
