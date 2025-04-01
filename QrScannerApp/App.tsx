import React, { useState, useEffect } from 'react';
import { Session } from '@supabase/supabase-js';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { supabase } from './lib/supabaseClient'; // Import our configured client
import AuthScreen from './screens/AuthScreen'; // We will create this screen
import HomeScreen from './screens/HomeScreen'; // We will create this screen

// Define types for our navigation stack parameters
export type RootStackParamList = {
  Auth: undefined; // No params expected for Auth screen
  Home: { session: Session }; // Home screen expects the session object
};

const Stack = createNativeStackNavigator<RootStackParamList>();

const App = () => {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check for existing session on mount
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setLoading(false);
    });

    // Listen for auth state changes
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      // No need to setLoading here as the initial check handles it
    });

    // Cleanup subscription on unmount
    return () => subscription.unsubscribe();
  }, []);

  if (loading) {
    // Optionally return a loading spinner component here
    return null;
  }

  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <Stack.Navigator>
          {session && session.user ? (
            // User is signed in - Show Home screen
            <Stack.Screen
              name="Home"
              component={HomeScreen}
              options={{ title: 'QR Scanner Home' }} // Example title
              initialParams={{ session: session }} // Pass session to Home
            />
          ) : (
            // No user session - Show Auth screen
            <Stack.Screen
              name="Auth"
              component={AuthScreen}
              options={{ headerShown: false }} // Hide header for Auth screen
            />
          )}
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
};

export default App;
