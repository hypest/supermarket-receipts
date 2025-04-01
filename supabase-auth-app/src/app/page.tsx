import { createClient } from '@/supabase/server';
import { redirect } from 'next/navigation';
import QrScanner from '@/components/QrScanner'; // Import the scanner component

export default async function Home() {
  const supabase = await createClient()

  const { data, error } = await supabase.auth.getUser()

  if (error || !data?.user) {
    redirect('/login')
  }

  const signOut = async () => {
    'use server'

    const supabase = await createClient()
    await supabase.auth.signOut()
    redirect('/login')
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen py-2">
      <main className="flex flex-col items-center justify-center w-full flex-1 px-20 text-center">
        <h1 className="text-4xl font-bold mb-6">
          Welcome!
        </h1>
        <p className="mb-4">You are logged in as: {data.user.email}</p>
        <form action={signOut}>
          <button
            type="submit"
            className="px-4 py-2 font-semibold text-white bg-blue-500 rounded hover:bg-blue-700"
          >
            Sign Out
          </button>
        </form>

        {/* Conditionally render QrScanner if user is logged in */}
        {data.user && <QrScanner userId={data.user.id} />}

      </main>
    </div>
  )
}
