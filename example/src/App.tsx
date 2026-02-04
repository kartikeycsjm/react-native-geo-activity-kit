import * as React from 'react';
import {
  StyleSheet,
  View,
  Text,
  Button,
  NativeEventEmitter,
  NativeModules,
  ScrollView,
  Platform,
} from 'react-native';
import type { EmitterSubscription } from 'react-native'; // <--- FIXED: Type-only import
import {
  startForegroundService,
  stopForegroundService,
  startMotionDetector,
  stopMotionDetector,
  type LocationEvent,
  type MotionEvent,
  type ErrorEvent,
} from 'react-native-geo-activity-kit';

const { GeoActivityKit } = NativeModules;

export default function App() {
  const [logs, setLogs] = React.useState<string[]>([]);
  const [isTracking, setIsTracking] = React.useState(false);

  const addLog = (msg: string) => {
    const time = new Date().toLocaleTimeString();
    setLogs((prev) => [`[${time}] ${msg}`, ...prev]);
  };

  React.useEffect(() => {
    const emitter = new NativeEventEmitter(GeoActivityKit);
    const subs: EmitterSubscription[] = [];

    // --- FIXED: Explicitly typed callbacks to satisfy TS ---
    const motionSub = emitter.addListener(
      'onMotionStateChanged',
      (event: any) => {
        const e = event as MotionEvent;
        addLog(`🏃 ${e.activity} (${e.state})`);
      }
    );

    const locationSub = emitter.addListener('onLocationLog', (event: any) => {
      const e = event as LocationEvent;
      addLog(`📍 ${e.latitude.toFixed(5)}, ${e.longitude.toFixed(5)}`);
    });

    const errorSub = emitter.addListener('onLocationError', (event: any) => {
      const e = event as ErrorEvent;
      addLog(`❌ Error: ${e.message}`);
    });

    subs.push(motionSub, locationSub, errorSub);

    return () => {
      subs.forEach((s) => s.remove());
    };
  }, []);

  const handleStart = async () => {
    try {
      if (Platform.OS === 'android') {
        await startForegroundService(
          'Tracking Active',
          'Monitoring location...',
          123
        );
        await startMotionDetector(75);
      }
      setIsTracking(true);
      addLog('✅ Service Started');
    } catch (e: any) {
      addLog(`Error: ${e.message}`);
    }
  };

  const handleStop = async () => {
    try {
      if (Platform.OS === 'android') {
        await stopForegroundService();
        await stopMotionDetector();
      }
      setIsTracking(false);
      addLog('🛑 Service Stopped');
    } catch (e: any) {
      addLog(`Error: ${e.message}`);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Geo Activity Kit</Text>

      <View style={styles.btnContainer}>
        <Button
          title="Start Tracking"
          onPress={handleStart}
          disabled={isTracking}
        />
        <View style={{ width: 20 }} />
        <Button
          title="Stop Tracking"
          onPress={handleStop}
          disabled={!isTracking}
          color="red"
        />
      </View>

      <ScrollView style={styles.logBox}>
        {logs.map((l, i) => (
          <Text key={i} style={styles.logText}>
            {l}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
    paddingHorizontal: 20,
    backgroundColor: '#f5f5f5',
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 30,
    color: '#333',
  },
  btnContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 20,
  },
  logBox: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 10,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  logText: {
    fontSize: 12,
    marginBottom: 5,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    color: '#333',
  },
});
