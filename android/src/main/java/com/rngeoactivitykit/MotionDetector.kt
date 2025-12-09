package com.rngeoactivitykit

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlin.math.sqrt

class MotionDetector(private val context: ReactApplicationContext, private val onStateChange: (String) -> Unit) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity = floatArrayOf(0f, 0f, 0f)
    private val linearAcceleration = floatArrayOf(0f, 0f, 0f)
    private val alpha: Float = 0.8f

    var motionThreshold: Float = 0.8f
    var startStabilityThreshold: Int = 20
    var stopStabilityThreshold: Int = 3000
    var samplingPeriodUs: Int = 100_000

    private var currentState: String = "STATIONARY"
    private var potentialState: String = "STATIONARY"
    private var consecutiveCount = 0
    private var isStarted = false

    fun start(threshold: Double): Boolean {
        if (accelerometer == null) return false
        
        motionThreshold = threshold.toFloat()
        currentState = "STATIONARY"
        potentialState = "STATIONARY"
        consecutiveCount = 0
        isStarted = true

        sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
        return true
    }

    fun stop() {
        isStarted = false
        sensorManager.unregisterListener(this)
    }
    
    fun setUpdateInterval(ms: Int) {
        samplingPeriodUs = ms * 1000
        if (isStarted) {
            stop()
            sensorManager.registerListener(this, accelerometer, samplingPeriodUs)
            isStarted = true
        }
    }

    fun isSensorAvailable(): Boolean = accelerometer != null

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            
            // Isolate Gravity
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

            // Remove Gravity
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
                
                // Notify Listener (SensorModule)
                onStateChange(currentState)

                // Emit to JS
                val params = Arguments.createMap()
                params.putString("state", currentState)
                if (context.hasActiveCatalystInstance()) {
                    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit("onMotionStateChanged", params)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}