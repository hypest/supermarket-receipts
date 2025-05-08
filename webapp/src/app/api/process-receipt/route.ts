import { NextRequest, NextResponse } from 'next/server';
import { SupabaseClient } from '@supabase/supabase-js'; // Import SupabaseClient type

// Configure Vercel function max duration (in seconds)
// Hobby: max 60, Pro: max 300, Enterprise: max 900
export const maxDuration = 60;
// Import the service role client creator instead of the default one
import { createServiceRoleClient } from '@/supabase/server';
import { getParserForUrl } from '@/lib/receipt-parsers'; // Import the registry function
import { ParsedReceiptData } from '@/lib/receipt-parsers/types'; // Import the shared type

// Define the expected structure of the incoming webhook payload
interface ScannedUrlPayload {
  type: 'INSERT';
  table: 'scanned_urls';
  schema: 'public';
  record: {
    id: number;
    url: string;
    user_id: string;
    created_at: string;
    html_content?: string | null; // Add optional html_content field
  };
  old_record: null;
}

// Helper function to update job status
// Making errorMessage strictly string | undefined
// Added type for supabase client
async function updateJobStatus(supabase: SupabaseClient, jobId: string, status: 'processing' | 'completed' | 'failed', errorMessage?: string) {
  // Define type for updateData
   const updateData: {
      status: string;
      updated_at: string;
      last_attempted_at?: string;
      error_message?: string | null;
   } = {
    status: status,
    updated_at: new Date().toISOString(),
  };
  if (status === 'processing') {
    updateData.last_attempted_at = new Date().toISOString();
  }
  // Only set error_message field if status is 'failed' and a message string is provided
  if (status === 'failed' && errorMessage) {
    updateData.error_message = errorMessage;
  } else if (status === 'failed') {
    // Set to null if status is failed but no message provided
     updateData.error_message = null;
  }
  const { error } = await supabase.from('receipt_processing_jobs').update(updateData).eq('id', jobId);
  if (error) console.error(`Failed to update job ${jobId} status to ${status}:`, error);
  else console.log(`Updated job ${jobId} status to ${status}`);
}


export async function POST(req: NextRequest) {
  console.log('Received request on /api/process-receipt');
  // Use the service role client for backend operations
  const supabase = createServiceRoleClient();

  // --- Security Check: Verify Supabase Webhook Secret ---
  const webhookSecret = process.env.SUPABASE_WEBHOOK_SECRET;
  if (!webhookSecret) {
      console.error('SUPABASE_WEBHOOK_SECRET environment variable is not set.');
      // Return 500 Internal Server Error if the secret isn't configured on the server
      return NextResponse.json({ error: 'Webhook secret not configured' }, { status: 500 });
  }

  // Supabase typically sends the secret in the 'x-supabase-webhook-secret' header (verify this in Supabase docs if needed)
  const receivedSecret = req.headers.get('x-supabase-webhook-secret');
  if (receivedSecret !== webhookSecret) {
      console.error('Invalid webhook secret received.');
      // Return 401 Unauthorized if the secret doesn't match
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
  console.log('Webhook secret verified successfully.');
  // --- End Security Check ---

  let payload: ScannedUrlPayload;
  try {
    payload = await req.json();
  } catch (error) {
    console.error('Failed to parse request body:', error);
    return NextResponse.json({ error: 'Invalid request body' }, { status: 400 });
  }

  // Validate payload
  if (payload.type !== 'INSERT' || payload.table !== 'scanned_urls' || !payload.record?.id || !payload.record?.url || !payload.record?.user_id) {
    console.warn('Ignoring invalid or non-INSERT event:', payload);
     return NextResponse.json({ message: 'Ignoring invalid event' }, { status: 200 });
  }

  // Extract data from payload, including the new html_content
  const { id: scannedUrlId, url: urlToFetch, user_id: userId, html_content: htmlContent } = payload.record;
  let jobId: string | null = null;

  try {
    // 1. Create/Check Job
    console.log(`Attempting to create job for scanned_url_id: ${scannedUrlId}`);
    const { data: newJob, error: insertJobError } = await supabase
      .from('receipt_processing_jobs')
      .insert({ scanned_url_id: scannedUrlId, status: 'pending', attempts: 1 })
      .select('id, attempts')
      .single();

    if (insertJobError) {
      if (insertJobError.code === '23505') { // Handle duplicate job
        console.log(`Job for scanned_url_id ${scannedUrlId} already exists or is being processed.`);
        return NextResponse.json({ message: 'Job already exists or processing' }, { status: 200 });
      } else {
        throw new Error(`Failed to create processing job: ${insertJobError.message}`);
      }
    }
    if (!newJob) throw new Error('Failed to create processing job (no data returned).');

    jobId = newJob.id;
    console.log(`Created new job ${jobId!} for scanned_url_id ${scannedUrlId}, attempt ${newJob.attempts}`);
    await updateJobStatus(supabase, jobId!, 'processing');

    // 2. Get the appropriate parser
    const parser = getParserForUrl(urlToFetch);
    if (!parser) {
      // If no parser found, mark job as failed with specific message
      throw new Error(`No suitable parser found for URL host: ${new URL(urlToFetch).hostname}`);
    }

    // 3. Execute the parser
    console.log(`Job ${jobId!}: Delegating parsing to selected parser...`);
    // Pass URL, optional HTML content, and Job ID to the parser
    const parsedData: ParsedReceiptData = await parser.parse(urlToFetch, jobId!, htmlContent ?? undefined); // Pass htmlContent if available
    console.log(`Job ${jobId!}: Parser returned ${parsedData.items.length} items.`);

    // Check if parser returned meaningful data (optional, depends on parser implementation)
    // Keep existing check, as even with HTML, parsing might yield nothing significant
    if (parsedData.items.length === 0 && !parsedData.headerInfo.total_amount && !parsedData.headerInfo.store_name) {
         const failMsg = 'Parser returned no significant data.';
         console.warn(`Job ${jobId!}: ${failMsg} Marking as failed.`);
         await updateJobStatus(supabase, jobId!, 'failed', failMsg); // Pass the string variable
         // Return 200 OK to webhook, as it's a parsing issue, not a server error
         return NextResponse.json({ message: 'Parsing yielded no significant data' }, { status: 200 });
    }

    // 4. Store results in Supabase (Receipt Header)
    console.log(`Job ${jobId!}: Inserting receipt header...`);
    const { data: newReceipt, error: insertReceiptError } = await supabase
      .from('receipts')
      .insert({
        scanned_url_id: scannedUrlId,
        user_id: userId,
        receipt_date: parsedData.headerInfo.receipt_date,
        total_amount: parsedData.headerInfo.total_amount,
        store_name: parsedData.headerInfo.store_name,
        uid: parsedData.headerInfo.uid, // Add the parsed UID here
      })
      .select('id')
      .single();

    if (insertReceiptError || !newReceipt) {
      throw new Error(`Failed to insert receipt header: ${insertReceiptError?.message}`);
    }
    const receiptId = newReceipt.id;
    console.log(`Job ${jobId!}: Inserted receipt header with ID: ${receiptId}`);

    // 5. Store results in Supabase (Receipt Items)
    if (parsedData.items.length > 0) {
        const itemsToInsert = parsedData.items.map(item => ({
          receipt_id: receiptId,
          name: item.name,
          quantity: item.quantity,
          price: item.price, // Total price
          unit_price: item.unit_price,
          vat_percentage: item.vat_percentage, // Add vat_percentage here
        }));

        console.log(`Job ${jobId!}: Inserting ${itemsToInsert.length} items...`);
        const { error: insertItemsError } = await supabase
          .from('receipt_items')
          .insert(itemsToInsert);

        if (insertItemsError) {
          // This is a partial failure state - header inserted, items failed.
          // Mark job as failed, but log clearly. Manual intervention might be needed.
          throw new Error(`Failed to insert receipt items after header insertion (Receipt ID: ${receiptId}): ${insertItemsError.message}`);
        }
        console.log(`Job ${jobId!}: Successfully inserted ${itemsToInsert.length} items for receipt ID: ${receiptId}`);
    } else {
        console.log(`Job ${jobId!}: No items to insert for receipt ID: ${receiptId}`);
    }

    // 6. Update job status to 'completed'
    await updateJobStatus(supabase, jobId!, 'completed', undefined); // Pass undefined when no error
    return NextResponse.json({ success: true, message: `Processed ${parsedData.items.length} items`, receiptId: receiptId }, { status: 200 });

  } catch (error: unknown) { // Use unknown for caught errors
    const logJobId = jobId ?? 'N/A';
    console.error(`Job ${logJobId}: Unhandled error processing URL ${urlToFetch}:`, error);
    // Type guard for error message extraction
    const errorMessage = error instanceof Error ? error.message : 'An unknown error occurred during processing.';

    // Update job status to 'failed' if we have a job ID
    if (jobId) { // Check if jobId is not null before using it
      // Pass the determined string errorMessage. The function signature allows string | null | undefined.
      await updateJobStatus(supabase, jobId, 'failed', errorMessage);
    } else {
      // Error occurred before job creation or retrieval
      console.error(`Processing failed before job could be confirmed for scanned_url_id ${scannedUrlId}. Error: ${errorMessage}`);
    }

    // Return 500 for server errors, maybe 4xx for specific parsing failures if desired
    return NextResponse.json({ error: errorMessage }, { status: 500 });
  }
}

// Optional: Remove edge runtime if using Node.js specific modules like fs (not used here currently)
// export const runtime = 'edge';
