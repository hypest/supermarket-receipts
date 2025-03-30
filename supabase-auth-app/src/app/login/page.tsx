'use client'

import { createClient } from '@/supabase/client'
import { Auth } from '@supabase/auth-ui-react'
import { ThemeSupa } from '@supabase/auth-ui-shared'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

export default function LoginPage() {
  const supabase = createClient()
  const router = useRouter()

  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (session) {
        // User is signed in, redirect to home page
        router.push('/')
      }
    })

    // Cleanup subscription on unmount
    return () => subscription.unsubscribe()
  }, [supabase, router])

  return (
    <div className="flex justify-center items-center min-h-screen">
      <div className="w-full max-w-md p-8 space-y-8 bg-white rounded-lg shadow-md">
        <Auth
          supabaseClient={supabase}
          appearance={{ theme: ThemeSupa }}
          theme="dark"
          providers={['github']} // Example: Add providers like GitHub, Google, etc.
          redirectTo={`${process.env.NEXT_PUBLIC_SITE_URL}/auth/callback`} // Ensure this matches your Supabase Auth settings
        />
      </div>
    </div>
  )
}
