# react-native-geo-activity-kit 📍 (Android Only)

Battery-efficient **background location & motion tracker** for React Native.

Unlike standard GPS libraries that drain battery in hours, this library uses the **Accelerometer (Low Power)** to intelligently toggle the **GPS (High Power)**. It includes a robust **Foreground Service** implementation to ensure background execution on modern Android versions (12/13/14+).

---

## 🧠 The "Smart Switch" Logic

### Stationary Mode (Zero/Low Drain)

- When the user is sitting still, the accelerometer detects no movement.
- GPS is switched to _Balanced Power Mode_ (low frequency) or effectively paused.
- Battery drain is negligible.

### Moving Mode (High Accuracy)

- When movement is detected (walking, driving), the library wakes up.
- GPS switches to _High Accuracy Mode_.
- Locations are logged at your configured interval.

---

## 📦 Installation

```bash
npm install react-native-geo-activity-kit
# or
yarn add react-native-geo-activity-kit
```

---

## 🤖 Android Setup

### 1. Update `AndroidManifest.xml`

Open `android/app/src/main/AndroidManifest.xml` and add the following permissions inside the `<manifest>` tag:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- Foreground Service (Crucial for Background Tasks) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- System -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

</manifest>
```

> 💡 You may not strictly need `ACCESS_BACKGROUND_LOCATION` if you always run as a Foreground Service, but it's included here for maximum compatibility. Adjust based on your Play Store policy needs.

---

## 🚀 Usage Guide

### 1. Request Permissions

Before starting, ensure you request the necessary runtime permissions (Location & Notifications).

### 2. Implementation Example

```tsx
import React, { useEffect, useState } from 'react';
import { View, Text, Button, PermissionsAndroid, Platform } from 'react-native';
import GeoKit from 'react-native-geo-activity-kit';

const App = () => {
  const [status, setStatus] = useState('Off');
  const [logs, setLogs] = useState<string[]>([]);

  useEffect(() => {
    // 1. Motion State Listener (STATIONARY vs MOVING)
    const motionSub = GeoKit.addMotionListener((event) => {
      console.log('Motion State:', event.state);
      setStatus(event.state);
    });

    // 2. Location Listener
    const locationSub = GeoKit.addLocationLogListener((loc) => {
      console.log('Location:', loc.latitude, loc.longitude);
      setLogs((prev) => [
        `${loc.timestamp}: ${loc.latitude}, ${loc.longitude}`,
        ...prev,
      ]);
    });

    // 3. Error Listener
    const errorSub = GeoKit.addLocationErrorListener((err) => {
      console.error('GeoKit Error:', err.message);
    });

    return () => {
      motionSub.remove();
      locationSub.remove();
      errorSub.remove();
    };
  }, []);

  const startService = async () => {
    try {
      // 1. Ask for permissions first (Standard Android Code)
      if (Platform.OS === 'android') {
        await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
          PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
        ]);
      }

      // 2. Start the Foreground Service (REQUIRED for background persistence)
      await GeoKit.startForegroundService(
        'Location Tracker',
        'Tracking your trips in the background...'
      );

      // 3. Start the Motion Detector (This activates the Smart Switch)
      // Threshold 0.8 is recommended for walking/driving detection.
      await GeoKit.startMotionDetector(0.8);

      setStatus('Started');
    } catch (e) {
      console.error(e);
    }
  };

  const stopService = async () => {
    await GeoKit.stopMotionDetector();
    await GeoKit.stopForegroundService();
    setStatus('Stopped');
  };

  return (
    <View style={{ padding: 20 }}>
      <Text style={{ fontSize: 18, marginBottom: 10 }}>Status: {status}</Text>
      <Button title="Start Tracking" onPress={startService} />
      <View style={{ height: 10 }} />
      <Button title="Stop Tracking" color="red" onPress={stopService} />

      <Text style={{ marginTop: 20, fontWeight: 'bold' }}>Logs:</Text>
      {logs.map((l, i) => (
        <Text key={i} style={{ fontSize: 10 }}>
          {l}
        </Text>
      ))}
    </View>
  );
};

export default App;
```

---

## 📚 API Reference

### Service Control

These methods control the sticky notification that keeps your app alive in the background.

| Method                              | Description                                                         |
| ----------------------------------- | ------------------------------------------------------------------- |
| `startForegroundService(title, body)` | Starts the Android Foreground Service. Required for background tracking. |
| `stopForegroundService()`          | Stops the service and removes the notification.                    |
| `updateServiceNotification(title, body)` | Updates the text of the running notification without restarting the service. |

---

### Configuration & Sensors

| Method                            | Default | Description                                                                 |
| --------------------------------- | ------- | --------------------------------------------------------------------------- |
| `startMotionDetector(threshold)`  | `0.8`   | Starts the accelerometer and GPS logic. `threshold` = force (G) to mark MOVING. |
| `stopMotionDetector()`            | -       | Stops the sensors and GPS updates.                                          |
| `setUpdateInterval(ms)`           | `100`   | How often the accelerometer checks for force, in milliseconds.              |
| `setLocationUpdateInterval(ms)`   | `90000` | How often GPS logs are captured when MOVING (90 seconds default).          |
| `setStabilityThresholds(start, stop)` | `20, 3000` | `start`: consecutive checks > threshold to become MOVING; `stop`: consecutive checks < threshold to become STATIONARY. |

---

### 🔔 Native Notifications

These methods trigger local notifications directly from the native module, ensuring they fire even if the JS thread is busy or backgrounded.

| Method                         | Description                                                                 |
| ------------------------------ | --------------------------------------------------------------------------- |
| `fireGenericAlert(title, body, id)` | Triggers a standard push notification immediately. `id` is a unique number. |
| `fireGeofenceAlert(type, userName)` | Triggers a pre-formatted Geofence alert. `type` must be `"IN"` or `"OUT"`.  |
| `cancelGenericAlert(id)`      | Cancels a specific notification by its ID.                                  |

---

## 📄 License
a
MIT