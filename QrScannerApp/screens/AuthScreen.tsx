import React, { useState } from 'react';
import { Alert, StyleSheet, View, TextInput, Button, Text } from 'react-native';
import { supabase } from '../lib/supabaseClient';

const AuthScreen = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  async function signInWithEmail() {
    setLoading(true);
    const { error } = await supabase.auth.signInWithPassword({
      email: email,
      password: password,
    });

    if (error) Alert.alert('Sign In Error', error.message);
    setLoading(false);
  }

  async function signUpWithEmail() {
    setLoading(true);
    const {
      data: { session }, // We might not need the session directly here
      error,
    } = await supabase.auth.signUp({
      email: email,
      password: password,
    });

    if (error) Alert.alert('Sign Up Error', error.message);
    // Supabase sends a confirmation email by default.
    // If no error, the onAuthStateChange listener in App.tsx might handle the session update,
    // or you might want to show a message asking the user to confirm their email.
    if (!error) Alert.alert('Sign Up Success', 'Please check your email for confirmation!');
    setLoading(false);
  }

  return (
    <View style={styles.container}>
      <View style={styles.verticallySpaced}>
        <Text style={styles.header}>QR Scanner App</Text>
      </View>
      <View style={[styles.verticallySpaced, styles.mt20]}>
        {/* <Text>Email</Text>  Optional: Add a separate Text label if desired */}
        <TextInput
          // label="Email" // Removed invalid prop
          onChangeText={(text) => setEmail(text)}
          value={email}
          placeholder="email@address.com"
          autoCapitalize={'none'}
          style={styles.input}
          keyboardType="email-address"
        />
      </View>
      <View style={styles.verticallySpaced}>
         {/* <Text>Password</Text> Optional: Add a separate Text label if desired */}
        <TextInput
          // label="Password" // Removed invalid prop
          onChangeText={(text) => setPassword(text)}
          value={password}
          secureTextEntry={true}
          placeholder="Password"
          autoCapitalize={'none'}
          style={styles.input}
        />
      </View>
      <View style={[styles.verticallySpaced, styles.mt20]}>
        <Button title="Sign in" disabled={loading} onPress={() => signInWithEmail()} />
      </View>
      <View style={styles.verticallySpaced}>
        <Button title="Sign up" disabled={loading} onPress={() => signUpWithEmail()} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: 40,
    padding: 12,
    flex: 1,
    justifyContent: 'center', // Center content vertically
  },
  verticallySpaced: {
    paddingTop: 4,
    paddingBottom: 4,
    alignSelf: 'stretch',
  },
  mt20: {
    marginTop: 20,
  },
  input: {
    height: 40,
    borderColor: 'gray',
    borderWidth: 1,
    marginBottom: 12,
    paddingHorizontal: 8,
    borderRadius: 4,
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 20,
  }
});

export default AuthScreen;
