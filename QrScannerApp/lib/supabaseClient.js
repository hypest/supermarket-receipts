import 'react-native-url-polyfill/auto'; // Required for Supabase to work in React Native
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createClient } from '@supabase/supabase-js';
import { SUPABASE_URL, SUPABASE_ANON_KEY } from '@env'; // Import credentials from .env via babel plugin

// Basic check if variables are loaded
if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
  console.error(
    'ERROR: Supabase URL or Anon Key is missing. Make sure you have set them in your .env file',
  );
  // You might want to throw an error here in a real app
}

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    storage: AsyncStorage, // Use AsyncStorage for session persistence in React Native
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: false, // Important for React Native, disable URL session detection
  },
});
