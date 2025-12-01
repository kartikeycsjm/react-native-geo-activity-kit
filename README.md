# react-native-geo-activity-kit 📍

A battery-efficient background location tracker for React Native.  
Most GPS libraries drain your battery in 3 hours. This library lasts 24+ hours.

---

## 🧠 How it Works (The "Smart Switch")

Standard location libraries keep the GPS chip (High Power) active 100% of the time.  
This library uses the **Accelerometer (Low Power)** to act as a smart GPS switch:

- **User Sits Still:** Accelerometer detects no movement → **GPS OFF 🔴** (Zero Battery Drain)  
- **User Walks/Drives:** Accelerometer detects force → **GPS ON 🟢** (High Accuracy)

---

## 📦 Installation

```
npm install react-native-geo-activity-kit
```

or

```
yarn add react-native-geo-activity-kit
```

---

## 🤖 Android Setup (Required)

File: `android/app/src/main/AndroidManifest.xml`  
Add this inside `<manifest>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 1. Location Permissions -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- 2. Service Permissions -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <!-- 3. Notification Permissions -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- 4. System Permissions -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

</manifest>
```

---

## 🚀 Quick Start Guide

### **Step 1: Ask for Permissions**

```js
import { PermissionsAndroid, Platform } from 'react-native';

const requestPermissions = async () => {
  if (Platform.OS === 'android') {
    await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    ]);
  }
};
```

---

### **Step 2: Start the Tracker**

`App.js` Example:

```js
import React, { useEffect, useState } from 'react';
import { View, Text, Button } from 'react-native';
import GeoKit from 'react-native-geo-activity-kit';

const App = () => {
  const [status, setStatus] = useState("Unknown");
  const [logs, setLogs] = useState([]);

  useEffect(() => {
    const motionListener = GeoKit.addMotionListener((event) => {
      console.log("Motion State:", event.state);
      setStatus(event.state);
    });

    const locListener = GeoKit.addLocationLogListener((loc) => {
      console.log("New Location:", loc.latitude, loc.longitude);
      setLogs(prev => [`${loc.latitude}, ${loc.longitude}`, ...prev]);
    });

    return () => {
      motionListener.remove();
      locListener.remove();
    };
  }, []);

  const startTracking = async () => {
    await GeoKit.startMotionDetector(0.8);
  };

  const stopTracking = () => {
    GeoKit.stopMotionDetector();
    setStatus("Stopped");
  };

  return (
    <View style={{ padding: 50 }}>
      <Text style={{ fontSize: 20 }}>Status: {status}</Text>
      <Button title="Start Tracking" onPress={startTracking} />
      <Button title="Stop Tracking" color="red" onPress={stopTracking} />
      {logs.map((l, i) => <Text key={i}>{l}</Text>)}
    </View>
  );
};

export default App;
```

---

## 🎛️ Configuration Guide

### **1. Motion Sensitivity (threshold)**  
- Controls how much force is needed to detect MOVING.  
- Range: **0.1 (very sensitive) → 2.0 (hard to trigger)**  
- Recommended: **0.8**

### **2. Motion Check Speed (setUpdateInterval)**  
- How often accelerometer checks movement.  
- Default: **100ms**

### **3. False Positive Protection (setStabilityThresholds)**  
- Prevents accidental GPS ON/OFF triggers.

Defaults:  
- **Start: 20 checks**  
- **Stop: 3000 checks**

### **4. GPS Frequency (setLocationUpdateInterval)**  
- How often GPS logs when MOVING.  
- Default: **90000 ms (90s)**

---

## 🔔 Native Notifications

```js
await GeoKit.fireGenericAlert(
  "Shift Ended",
  "Please mark your attendance out.",
  101
);

await GeoKit.fireGeofenceAlert("IN", "John Doe"); 
await GeoKit.fireGeofenceAlert("OUT", "John Doe");

await GeoKit.cancelGenericAlert(101);
```

---

## 📚 API Reference

| Method | Description |
|--------|-------------|
| startMotionDetector(threshold) | Starts sensors. |
| stopMotionDetector() | Stops sensors + GPS. |
| setUpdateInterval(ms) | Accelerometer interval. |
| setStabilityThresholds(start, stop) | Samples required to switch states. |
| setLocationUpdateInterval(ms) | GPS log interval while moving. |
| fireGenericAlert(title, body, id) | Push notification. |
| fireGeofenceAlert(type, name) | Enter/Exit notification. |
| isAvailable() | Check accelerometer availability. |

---

## ❓ Troubleshooting

### **1. Walking but no location logs?**
- If Motion State = **STATIONARY**, GPS is OFF.
- Shake the device to trigger MOVING.

### **2. Android 14 Crash?**
Add:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

### **3. Need Physical Activity permission?**
❌ **No.**  
Accelerometer does not require the ACTIVITY_RECOGNITION permission.

---

## 📄 License

MIT# react-native-geo-activity-kit
# react-native-geo-activity-kit
# react-native-geo-activity-kit
