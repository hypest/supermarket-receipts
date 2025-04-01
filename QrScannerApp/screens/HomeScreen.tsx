import React from 'react';
import { StyleSheet, View, Text, Button, Alert } from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { supabase } from '../lib/supabaseClient';
import { RootStackParamList } from '../App'; // Import the stack param list type
import QrScannerComponent from '../components/QrScannerComponent'; // We will create this

// Define props type for HomeScreen using the navigation stack param list
type HomeScreenProps = NativeStackScreenProps<RootStackParamList, 'Home'>;

const HomeScreen = ({ route }: HomeScreenProps) => {
  const { session } = route.params; // Get session passed from App.tsx

  const handleSignOut = async () => {
    const { error } = await supabase.auth.signOut();
    if (error) {
      Alert.alert('Sign Out Error', error.message);
    }
    // The onAuthStateChange listener in App.tsx will handle navigation back to AuthScreen
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text>Welcome!</Text>
        {/* Display user email if available */}
        {session?.user?.email && <Text>Logged in as: {session.user.email}</Text>}
        <Button title="Sign Out" onPress={handleSignOut} />
      </View>

      {/* QR Scanner Section */}
      <View style={styles.scannerContainer}>
        {session?.user && <QrScannerComponent userId={session.user.id} />}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 10,
  },
  header: {
    marginBottom: 20,
    alignItems: 'center',
  },
  scannerContainer: {
    flex: 1, // Allow scanner to take remaining space
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 5,
    overflow: 'hidden', // Keep camera view contained
    marginTop: 10,
  },
});

export default HomeScreen;
