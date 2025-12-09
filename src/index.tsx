import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-geo-activity-kit' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

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
  // Service Control
  startForegroundService: (title: string, body: string): Promise<boolean> =>
    RNSensorModule.startForegroundService(title, body),

  stopForegroundService: (): Promise<boolean> =>
    RNSensorModule.stopForegroundService(),

  updateServiceNotification: (title: string, body: string): Promise<boolean> =>
    RNSensorModule.updateServiceNotification(title, body),

  // Sensors & Configuration
  startMotionDetector: (threshold: number = 0.8) =>
    RNSensorModule.startMotionDetector(threshold),

  stopMotionDetector: () => RNSensorModule.stopMotionDetector(),

  setUpdateInterval: (ms: number = 100) => RNSensorModule.setUpdateInterval(ms),

  setLocationUpdateInterval: (ms: number = 90000) =>
    RNSensorModule.setLocationUpdateInterval(ms),

  setStabilityThresholds: (start: number = 20, stop: number = 3000) =>
    RNSensorModule.setStabilityThresholds(start, stop),

  // Alerts
  fireGeofenceAlert: (type: string, userName: string) =>
    RNSensorModule.fireGeofenceAlert(type, userName),

  fireGenericAlert: (title: string, body: string, id: number) =>
    RNSensorModule.fireGenericAlert(title, body, id),

  cancelGenericAlert: (id: number) => RNSensorModule.cancelGenericAlert(id),

  isAvailable: () => RNSensorModule.isAvailable(),

  // Listeners
  addMotionListener: (cb: (event: any) => void) =>
    emitter.addListener('onMotionStateChanged', cb),

  addLocationLogListener: (cb: (event: any) => void) =>
    emitter.addListener('onLocationLog', cb),

  addLocationErrorListener: (cb: (event: any) => void) =>
    emitter.addListener('onLocationError', cb),
};
