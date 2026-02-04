import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import type { EventSubscription } from 'react-native';

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

export interface LocationEvent {
  latitude: number;
  longitude: number;
  accuracy: number;
  timestamp: string;
  is_mock: boolean;
}

export interface MotionEvent {
  activity:
    | 'STILL'
    | 'WALKING'
    | 'RUNNING'
    | 'ON_BICYCLE'
    | 'IN_VEHICLE'
    | 'UNKNOWN';
  isMoving: boolean;
  state: 'MOVING' | 'STATIONARY';
}

export interface ErrorEvent {
  error: string;
  message: string;
}

export const startForegroundService = (
  title: string,
  body: string,
  id: number
): Promise<boolean> => RNSensorModule.startForegroundService(title, body, id);

export const stopForegroundService = (): Promise<boolean> =>
  RNSensorModule.stopForegroundService();

export const updateServiceNotification = (
  title: string,
  body: string
): Promise<boolean> => RNSensorModule.updateServiceNotification(title, body);

export const startMotionDetector = (
  confidence: number = 75
): Promise<boolean> => RNSensorModule.startMotionDetector(confidence);

export const stopMotionDetector = (): Promise<boolean> =>
  RNSensorModule.stopMotionDetector();

export const setLocationUpdateInterval = (ms: number): Promise<void> =>
  RNSensorModule.setLocationUpdateInterval(ms);

export const fireGeofenceAlert = (
  type: 'IN' | 'OUT',
  userName: string
): Promise<boolean> => RNSensorModule.fireGeofenceAlert(type, userName);

export const fireGenericAlert = (
  title: string,
  body: string,
  id: number
): Promise<boolean> => RNSensorModule.fireGenericAlert(title, body, id);

export const cancelGenericAlert = (id: number): Promise<boolean> =>
  RNSensorModule.cancelGenericAlert(id);

export const registerGpsListener = (): Promise<boolean> =>
  RNSensorModule.registerGpsListener();

export const addGpsStatusListener = (
  cb: (event: { enabled: boolean }) => void
): EventSubscription =>
  emitter.addListener('onGpsStatusChanged', (event: any) => cb(event));

export const addMotionListener = (
  cb: (event: MotionEvent) => void
): EventSubscription =>
  emitter.addListener('onMotionStateChanged', (event: any) => cb(event));

export const addLocationLogListener = (
  cb: (event: LocationEvent) => void
): EventSubscription =>
  emitter.addListener('onLocationLog', (event: any) => cb(event));

export const addLocationErrorListener = (
  cb: (event: ErrorEvent) => void
): EventSubscription =>
  emitter.addListener('onLocationError', (event: any) => cb(event));

// Default export for backward compatibility
export default {
  startForegroundService,
  stopForegroundService,
  updateServiceNotification,
  startMotionDetector,
  stopMotionDetector,
  setLocationUpdateInterval,
  fireGeofenceAlert,
  fireGenericAlert,
  cancelGenericAlert,
  registerGpsListener,
  addGpsStatusListener,
  addMotionListener,
  addLocationLogListener,
  addLocationErrorListener,
};
