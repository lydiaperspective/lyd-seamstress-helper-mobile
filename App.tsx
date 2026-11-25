import React, { useEffect, useState } from 'react';
import { ActivityIndicator, StatusBar, StyleSheet, Text, View } from 'react-native';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { initializeDatabase } from './src/data/database';
import { ProjectsScreen } from './src/screens/ProjectsScreen';
import { ProjectScreen } from './src/screens/ProjectScreen';
import type { Project } from './src/types';

export type RootStackParamList = {
  Projects: undefined;
  Project: { project: Project };
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const navigationTheme = {
  ...DefaultTheme,
  colors: { ...DefaultTheme.colors, background: '#F5F1EA', card: '#FBF9F5', text: '#282722', border: '#E7E0D6', primary: '#657765' },
};

export default function App() {
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    initializeDatabase()
      .then(() => setReady(true))
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Could not open project storage.'));
  }, []);

  if (!ready) {
    return (
      <SafeAreaProvider>
        <View style={styles.loading}>
          {error ? <Text style={styles.error}>{error}</Text> : <ActivityIndicator size="large" color="#657765" />}
          {!error && <Text style={styles.loadingText}>Preparing your studio…</Text>}
        </View>
      </SafeAreaProvider>
    );
  }

  return (
    <SafeAreaProvider>
      <NavigationContainer theme={navigationTheme}>
        <StatusBar barStyle="dark-content" backgroundColor="#F5F1EA" />
        <Stack.Navigator>
          <Stack.Screen name="Projects" component={ProjectsScreen} options={{ headerShown: false }} />
          <Stack.Screen name="Project" component={ProjectScreen} options={{ headerShown: false }} />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F5F1EA', gap: 12 },
  loadingText: { color: '#77756D', fontSize: 15 },
  error: { color: '#A34335', padding: 24, textAlign: 'center' },
});
