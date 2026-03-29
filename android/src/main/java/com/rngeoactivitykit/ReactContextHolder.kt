package com.rngeoactivitykit

import com.facebook.react.bridge.ReactApplicationContext

// Holds a reference to the latest ReactApplicationContext for background components.
object ReactContextHolder {
    @Volatile
    private var reactContext: ReactApplicationContext? = null

    fun set(context: ReactApplicationContext) {
        reactContext = context
    }

    fun get(): ReactApplicationContext? = reactContext

    fun clear() {
        reactContext = null
    }
}
