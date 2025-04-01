import { createServerClient, type CookieOptions } from '@supabase/ssr'
import { createClient as createSupabaseClient } from '@supabase/supabase-js'
import { cookies } from 'next/headers'

// Client for operations respecting RLS and user context (e.g., in Server Components)
export async function createClient() {
  // cookies() returns ReadonlyRequestCookies synchronously in this context
  const cookieStore = await cookies()

  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        get(name: string) {
          // Access the value from the cookie store
          return cookieStore.get(name)?.value
        },
        // NOTE: Setting/removing cookies using this client requires it to be called
        // from a context that supports modifying cookies (API Routes, Server Actions).
        // It will error if called from a Server Component during rendering.
        set(name: string, value: string, options: CookieOptions) {
          try {
             cookieStore.set({ name, value, ...options })
          } catch {
             // Ignore errors when attempting to set cookies from incompatible contexts
          }
        },
        remove(name: string, options: CookieOptions) {
          try {
             cookieStore.set({ name, value: '', ...options })
          } catch {
             // Ignore errors when attempting to remove cookies from incompatible contexts
          }
        },
      },
    }
  )
}


// Client for backend operations bypassing RLS (e.g., in API routes performing admin/service tasks)
// IMPORTANT: Ensure SUPABASE_SERVICE_ROLE_KEY is set in your environment variables ONLY on the server.
export function createServiceRoleClient() {
    const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

    if (!supabaseUrl || !serviceKey) {
        throw new Error('Supabase URL or Service Role Key environment variable is missing.');
    }

    // Use the standard createClient from @supabase/supabase-js for service role
    return createSupabaseClient(supabaseUrl, serviceKey, {
        auth: {
            persistSession: false,
            autoRefreshToken: false,
            detectSessionInUrl: false,
        },
    });
}
