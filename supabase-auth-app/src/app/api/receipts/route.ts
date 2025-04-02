import { createClient } from '@/supabase/server'; // Correct import
import { NextResponse } from 'next/server';
// cookies are handled internally by createClient now
// import { cookies } from 'next/headers';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export async function GET(_request: Request) { // Add ESLint disable comment
  // const cookieStore = cookies(); // No longer needed here
  const supabase = await createClient(); // Use the correct async function

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser();

  if (authError || !user) {
    console.error('GET /api/receipts: Auth error', authError);
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  // Fetch receipts for the logged-in user, ordered by date descending
  // Also fetch related receipt_items
  const { data: receipts, error: fetchError } = await supabase
    .from('receipts')
    .select(`
      id,
      receipt_date,
      total_amount,
      store_name,
      uid,
      created_at,
      receipt_items (
        id,
        name,
        quantity,
        price
      )
    `)
    .eq('user_id', user.id)
    .order('receipt_date', { ascending: false, nullsFirst: false }) // Show most recent first
    .order('created_at', { referencedTable: 'receipt_items', ascending: true }); // Keep item order consistent if needed

  if (fetchError) {
    console.error('GET /api/receipts: Fetch error', fetchError);
    return NextResponse.json({ error: 'Failed to fetch receipts', details: fetchError.message }, { status: 500 });
  }

  // console.log(`GET /api/receipts: Fetched ${receipts?.length ?? 0} receipts for user ${user.id}`);

  return NextResponse.json(receipts ?? []);
}
