import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  EmitterSubscription,
} from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-geo-activity-kit' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// 1. Define the Native Module
const RNSensorModule = NativeModules.RNSensorModule
  ? NativeModules.RNSensorModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const emitter = new NativeEventEmitter(RNSensorModule);

// 2. Define Data Types (Interfaces)
export interface LocationEvent {
  latitude: number;
  longitude: number;
  timestamp: string;
}

export interface MotionEvent {
  state: 'MOVING' | 'STATIONARY';
}

export interface ErrorEvent {
  error: string;
  message: string;
}

// 3. Define the Shape of the Library
export interface SensorModuleType {
  startMotionDetector(threshold?: number): Promise<boolean>;
  stopMotionDetector(): Promise<boolean>;
  setUpdateInterval(ms?: number): Promise<boolean>;
  setLocationUpdateInterval(ms?: number): Promise<boolean>;
  setStabilityThresholds(
    startThreshold?: number,
    stopThreshold?: number
  ): Promise<boolean>;
  fireGeofenceAlert(type: string, userName: string): Promise<boolean>;
  fireGenericAlert(title: string, body: string, id: number): Promise<boolean>;
  cancelGenericAlert(id: number): Promise<boolean>;
  isAvailable(): Promise<{ accelerometer: boolean }>;
  addMotionListener(cb: (event: MotionEvent) => void): EmitterSubscription;
  addLocationLogListener(
    cb: (event: LocationEvent) => void
  ): EmitterSubscription;
  addLocationErrorListener(
    cb: (event: ErrorEvent) => void
  ): EmitterSubscription;
}

// 4. Implement with Explicit Types
const GeoKit: SensorModuleType = {
  startMotionDetector: (threshold: number = 0.8): Promise<boolean> =>
    RNSensorModule.startMotionDetector(threshold),

  stopMotionDetector: (): Promise<boolean> =>
    RNSensorModule.stopMotionDetector(),

  setUpdateInterval: (ms: number = 100): Promise<boolean> =>
    RNSensorModule.setUpdateInterval(ms),

  setLocationUpdateInterval: (ms: number = 90000): Promise<boolean> =>
    RNSensorModule.setLocationUpdateInterval(ms),

  setStabilityThresholds: (
    startThreshold: number = 20,
    stopThreshold: number = 3000
  ): Promise<boolean> =>
    RNSensorModule.setStabilityThresholds(startThreshold, stopThreshold),

  fireGeofenceAlert: (type: string, userName: string): Promise<boolean> =>
    RNSensorModule.fireGeofenceAlert(type, userName),

  fireGenericAlert: (
    title: string,
    body: string,
    id: number
  ): Promise<boolean> => RNSensorModule.fireGenericAlert(title, body, id),

  cancelGenericAlert: (id: number): Promise<boolean> =>
    RNSensorModule.cancelGenericAlert(id),

  isAvailable: (): Promise<{ accelerometer: boolean }> =>
    RNSensorModule.isAvailable(),

  addMotionListener: (cb: (event: MotionEvent) => void): EmitterSubscription =>
    emitter.addListener('onMotionStateChanged', cb),

  addLocationLogListener: (
    cb: (event: LocationEvent) => void
  ): EmitterSubscription => emitter.addListener('onLocationLog', cb),

  addLocationErrorListener: (
    cb: (event: ErrorEvent) => void
  ): EmitterSubscription => emitter.addListener('onLocationError', cb),
};

export default GeoKit;
