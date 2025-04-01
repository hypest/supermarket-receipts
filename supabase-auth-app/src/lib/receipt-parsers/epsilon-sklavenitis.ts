import axios from 'axios';
import { ReceiptParser, ParsedReceiptData } from './types';

// Parser for receipts hosted on epsilondigital-sklavenitis.epsilonnet.gr
const epsilonSklavenitisParser: ReceiptParser = {
  parse: async (url: string, jobId?: string): Promise<ParsedReceiptData> => {
    const logPrefix = jobId ? `Job ${jobId}: ` : '';
    console.log(`${logPrefix}Using Epsilon/Sklavenitis parser for URL: ${url}`);

    const headerInfo: ParsedReceiptData['headerInfo'] = {
        receipt_date: null,
        total_amount: null,
        store_name: 'ΣΚΛΑΒΕΝΙΤΗΣ', // Derived from hostname
        uid: null,
    };
    const items: ParsedReceiptData['items'] = [];

    try {
      // Fetch the HTML - primarily to confirm reachability and potentially extract basic info if structure changes
      console.log(`${logPrefix}Fetching URL: ${url}`);
      await axios.get<string>(url, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36' },
        timeout: 15000,
        responseType: 'text'
      });
      // const html = response.data;
      // console.log(`${logPrefix}Fetched HTML (length: ${html.length})`);

      // --- WARNING ---
      // The actual receipt data on this page is rendered by Blazor WebAssembly
      // and embedded in an encoded format within the initial HTML source.
      // Reliably parsing this requires browser automation (e.g., Puppeteer/Playwright)
      // which is not available in this environment.
      // Returning only the store name derived from the URL.
      console.warn(`${logPrefix}Cannot parse detailed receipt data from Blazor page structure for ${url}. Returning store name only.`);
      // --- END WARNING ---

      // Future enhancement: If browser automation becomes available, implement parsing here.
      // For now, we only have the store name.

    } catch (error: unknown) {
        const errorMsg = error instanceof Error ? error.message : String(error);
        console.error(`${logPrefix}Error fetching or processing Epsilon/Sklavenitis URL ${url}: ${errorMsg}`);
        // Re-throw the error to mark the job as failed in the calling API route
        throw new Error(`Failed to fetch or process Epsilon/Sklavenitis URL: ${errorMsg}`);
    }

    return { headerInfo, items };
  }
};

export default epsilonSklavenitisParser;
