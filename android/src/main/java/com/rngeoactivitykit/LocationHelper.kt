package com.rngeoactivitykit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback
    private var locationRequest: LocationRequest
    
    var isLocationClientRunning: Boolean = false
        private set

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        locationRequest = LocationRequest.create().apply {
            interval = 30000
            fastestInterval = 30000
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
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

                sendEvent("onLocationLog", params)
            }
        }
    }

    fun updateLocationRequest(priority: Int, intervalMs: Long) {
        if (locationRequest.priority == priority && locationRequest.interval == intervalMs) return

        Log.i("LocationHelper", "Switching Mode -> Priority: $priority, Interval: $intervalMs")

        locationRequest = LocationRequest.create().apply {
            this.interval = intervalMs
            this.fastestInterval = intervalMs
            this.priority = priority
        }
        
        // Restart if running to apply changes
        if (isLocationClientRunning) {
            stopLocationUpdates()
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isLocationClientRunning) return
        if (!hasLocationPermission()) {
            val params = Arguments.createMap()
            params.putString("error", "LOCATION_PERMISSION_DENIED")
            params.putString("message", "Location permission is not granted.")
            sendEvent("onLocationError", params)
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            isLocationClientRunning = true
            Log.i("LocationHelper", "Location updates started.")
        } catch (e: Exception) {
            val params = Arguments.createMap()
            params.putString("error", "START_LOCATION_FAILED")
            params.putString("message", "Error starting location: ${e.message}")
            sendEvent("onLocationError", params)
        }
    }

    fun stopLocationUpdates() {
        if (!isLocationClientRunning) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationClientRunning = false
            Log.i("LocationHelper", "Location updates stopped.")
        } catch (e: Exception) {
            Log.e("LocationHelper", "Failed to stop location updates: " + e.message)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun sendEvent(eventName: String, params: Any?) {
        if (context.hasActiveCatalystInstance()) {
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
        }
    }
}