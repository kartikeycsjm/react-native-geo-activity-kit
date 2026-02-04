package com.rngeoactivitykit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class LocationHelper(private val context: ReactApplicationContext) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var shared: LocationHelper? = null
    }

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback
    private var locationRequest: LocationRequest
    
    var isLocationClientRunning: Boolean = false
        private set

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        shared = this
        
        // PROD GRADE: Start with 5 Minutes (Battery Saver)
        // We assume the user is stationary until the Motion Detector proves otherwise.
        val defaultInterval = 300000L // 5 Minutes

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
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            isLocationClientRunning = true
            Log.d("LocationHelper", "✅ Location Updates STARTED.")
        } catch (e: Exception) {
            Log.e("LocationHelper", "Error starting location: ${e.message}")
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
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun sendEvent(eventName: String, params: Any?) {
        try {
            if (context.hasActiveCatalystInstance()) {
                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "JS Error: ${e.message}")
        }
    }
}