'use server';

import { createClient } from '@/supabase/server'; // Use server client
import { revalidatePath } from 'next/cache'; // To potentially help refresh server components if needed, though realtime should handle client

export async function deleteReceiptAction(receiptId: string): Promise<{ success: boolean; error?: string }> {
  const supabase = await createClient(); // Await the client creation

  // Verify user is authenticated
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) {
    return { success: false, error: 'Authentication required.' };
  }

  console.log(`Attempting to delete receipt ${receiptId} for user ${user.id}`);

  try {
    // Delete the receipt matching the ID and the authenticated user's ID
    // This relies on RLS policies being correctly set up for DELETE operations
    const { error } = await supabase
      .from('receipts')
      .delete()
      .match({ id: receiptId, user_id: user.id }); // Ensure user can only delete their own receipts

    if (error) {
      console.error(`Error deleting receipt ${receiptId}:`, error);
      throw new Error(error.message);
    }

    console.log(`Successfully deleted receipt ${receiptId}`);
    // Revalidate the path to potentially trigger data refresh for server components,
    // although the client-side realtime listener should handle the immediate UI update.
    revalidatePath('/receipts');
    return { success: true };

  } catch (e: unknown) {
    const errorMessage = e instanceof Error ? e.message : 'Failed to delete receipt.';
    console.error(`Catch block error deleting receipt ${receiptId}:`, errorMessage);
    return { success: false, error: errorMessage };
  }
}
