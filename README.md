# React Native Geo Activity Kit

A **production-grade, battery-efficient background tracking and activity recognition library for React Native**.

This library is designed for **workforce management and tracking apps**. It combines **Google Fused Location Provider** with the **Activity Recognition Transition API** to create a **Smart Tracking Engine** that automatically adjusts GPS frequency based on user movement — ensuring **high accuracy when moving** and **near-zero battery drain when stationary**.

---

## 🚀 Key Features

### 🧠 Smart Battery Saver
- **Stationary Mode**: Defaults to **5-minute intervals** when the device is still.
- **High-Speed Mode**: Automatically switches to **30-second intervals** the millisecond movement is detected.

### ⚡ Zero-Latency Wake Up
- Uses **EXIT STILL** transitions to trigger GPS immediately when a user stands up or picks up the phone.

### 🛡️ Foreground Service
- Runs reliably in the background with a persistent notification.
- Fully compatible with **Android 14** foreground service requirements.

### 🔔 Local Notification System
- **Geofence Alerts**
  - **Entry (Green)** and **Exit (Red)** pre-styled notifications.
- **Generic Alerts**
  - Trigger custom notifications for background push messages or updates.

### 🏃 Motion Detection
Detects the following activities:
- STILL
- WALKING
- RUNNING
- IN_VEHICLE
- ON_BICYCLE

### 🛰️ GPS Status Monitoring
- Detects when the user physically toggles the **System Location (GPS)** switch ON/OFF.

### 🚫 Fake GPS Detection
- Every location update includes an `is_mock` flag to detect spoofed locations.

---

## 📦 Installation

```bash
npm install react-native-geo-activity-kit
# or
yarn add react-native-geo-activity-kit
```

---

## ⚙️ Android Configuration (Required)

Because this library runs continuously in the background, **AndroidManifest.xml configuration is mandatory**.

### 1️⃣ Add Permissions

Open `android/app/src/main/AndroidManifest.xml` and add:

```xml
<manifest ...>

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application ...>
```

---

### 2️⃣ Register Services & Receivers

Inside the `<application>` tag:

```xml
<application ...>

    <service
        android:name="com.rngeoactivitykit.TrackingService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="location" />

    <receiver
        android:name="com.rngeoactivitykit.ActivityTransitionReceiver"
        android:exported="false"
        android:permission="com.google.android.gms.permission.ACTIVITY_RECOGNITION">
        <intent-filter>
            <action android:name="com.rngeoactivitykit.ACTION_PROCESS_ACTIVITY_TRANSITIONS" />
        </intent-filter>
    </receiver>

</application>
```

---

## 📖 Usage Guide

### 1️⃣ Request Permissions

```javascript
import { PermissionsAndroid, Platform } from 'react-native';

async function requestPermissions() {
  if (Platform.OS === 'android') {
    await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      PermissionsAndroid.PERMISSIONS.ACCESS_BACKGROUND_LOCATION,
      PermissionsAndroid.PERMISSIONS.ACTIVITY_RECOGNITION,
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    ]);
  }
}
```

---

### 2️⃣ Start the Smart Tracking Service

```javascript
import GeoKit from 'react-native-geo-activity-kit';

const startTracking = async () => {
  await GeoKit.startForegroundService(
    'Workforce Tracking',
    'Monitoring location in background...',
    9991
  );

  await GeoKit.startMotionDetector();
};
```

---

### 3️⃣ Listen for Data

```javascript
import { useEffect } from 'react';
import GeoKit from 'react-native-geo-activity-kit';

useEffect(() => {
  const locationSub = GeoKit.addLocationLogListener((event) => {
    console.log(event.latitude, event.longitude);

    if (event.is_mock) {
      console.warn('FAKE GPS DETECTED');
    }
  });

  const motionSub = GeoKit.addMotionListener((event) => {
    console.log(event.activity, event.isMoving);
  });

  const gpsSub = GeoKit.addGpsStatusListener((event) => {
    if (!event.enabled) {
      console.error('GPS turned OFF');
    }
  });

  return () => {
    locationSub.remove();
    motionSub.remove();
    gpsSub.remove();
  };
}, []);
```

---

## 🔔 Notification API

### Geofence Alerts

```javascript
GeoKit.fireGeofenceAlert('OUT', 'John Doe');
GeoKit.fireGeofenceAlert('IN', 'John Doe');
```

---

### Generic Alerts

```javascript
GeoKit.fireGenericAlert('New Task', 'You have a new site visit.', 1001);
```

---

### Cancel Notification

```javascript
GeoKit.cancelGenericAlert(1001);
```

---

## 📚 API Reference

### Service Control

| Method | Description |
|------|------------|
| startForegroundService | Starts persistent background service |
| stopForegroundService | Stops service |
| updateServiceNotification | Updates notification text |

---

### Motion & Location

| Method | Description |
|------|------------|
| startMotionDetector | Enables activity recognition |
| stopMotionDetector | Stops motion detection |
| setLocationUpdateInterval | Manual GPS override |
| registerGpsListener | Monitor GPS toggle |

---

## 📱 Supported Platforms

- **Android**: SDK 29 (Android 10) to SDK 34 (Android 14)
- **iOS**: Not supported

---

## 🧩 Troubleshooting

### Service crashes on start
Ensure notification ID is an integer.

### Location stops after 20 minutes
This is expected behavior in stationary mode.

### Fake GPS not detected
Ensure a mock location app is actively spoofing.

---

## 📄 License

MIT


---

## 👤 Author

**Kartikey Mishra**  
Creator & Maintainer of React Native Geo Activity Kit
