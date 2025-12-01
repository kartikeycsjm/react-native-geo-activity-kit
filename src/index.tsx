import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-geo-activity-kit' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// The name must match getName() in SensorModule.kt
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

export default {
  startMotionDetector: (threshold: number = 0.8) =>
    RNSensorModule.startMotionDetector(threshold),

  stopMotionDetector: () => RNSensorModule.stopMotionDetector(),

  setUpdateInterval: (ms: number = 100) => RNSensorModule.setUpdateInterval(ms),

  setLocationUpdateInterval: (ms: number = 90000) =>
    RNSensorModule.setLocationUpdateInterval(ms),

  setStabilityThresholds: (
    startThreshold: number = 20,
    stopThreshold: number = 3000
  ) => RNSensorModule.setStabilityThresholds(startThreshold, stopThreshold),

  // Added types: string for text inputs
  fireGeofenceAlert: (type: string, userName: string) =>
    RNSensorModule.fireGeofenceAlert(type, userName),

  // Added types: string for text, number for ID (assuming Android notification ID)
  fireGenericAlert: (title: string, body: string, id: number) =>
    RNSensorModule.fireGenericAlert(title, body, id),

  // Added type: number to match the ID above
  cancelGenericAlert: (id: number) => RNSensorModule.cancelGenericAlert(id),

  isAvailable: () => RNSensorModule.isAvailable(),

  // Added type: function that accepts an event (any)
  addMotionListener: (cb: (event: any) => void) =>
    emitter.addListener('onMotionStateChanged', cb),

  addLocationLogListener: (cb: (event: any) => void) =>
    emitter.addListener('onLocationLog', cb),

  addLocationErrorListener: (cb: (event: any) => void) =>
    emitter.addListener('onLocationError', cb),
};
