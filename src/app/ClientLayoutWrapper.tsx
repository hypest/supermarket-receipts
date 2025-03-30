'use client';

import { SessionContextProvider, useSession } from "@supabase/auth-helpers-react";
import supabase from "../../lib/supabaseClient"; // Adjust path if necessary

export default function ClientLayoutWrapper({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = useSession(); // useSession hook needs to be inside the provider

  return (
    <SessionContextProvider supabaseClient={supabase}>
      {/* Navigation logic moved here as it depends on the session */}
      <nav>
        <ul>
          {session ? (
            <>
              <li><p>Welcome, my friend!</p></li> {/* Welcome message */}
              <li><a href="/auth/logout">Logout</a></li> {/* Logout button */}
            </>
          ) : (
            <>
              <li><a href="/">Home</a></li>
              <li><a href="/auth/register">Register</a></li>
              <li><a href="/auth/login">Login</a></li> {/* Login button */}
            </>
          )}
        </ul>
      </nav>
      {children}
    </SessionContextProvider>
  );
}
