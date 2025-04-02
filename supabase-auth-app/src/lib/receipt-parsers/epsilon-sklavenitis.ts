// NOTE: This parser now RELIES on the client (e.g., Android WebView)
// to provide the fully rendered HTML content, as direct fetching/Puppeteer
// is blocked by Cloudflare or too slow/unreliable in serverless.

import * as cheerio from 'cheerio';
import { ReceiptParser, ParsedReceiptData } from './types';

// --- NO LONGER USING PUPPETEER IN THIS PARSER ---
// Imports for puppeteer, chromium, Browser, Page are removed.
// Browser management functions (getBrowser, closeBrowserInstance) are removed.
// Process exit hooks are removed.

// --- Parser Implementation ---
const epsilonSklavenitisParser: ReceiptParser = {
  // Updated signature to accept optional htmlContent
  parse: async (url: string, jobId?: string, htmlContent?: string): Promise<ParsedReceiptData> => {
    const logPrefix = jobId ? `Job ${jobId}: ` : '';
    console.log(`${logPrefix}Using Epsilon/Sklavenitis Cheerio-only parser for URL: ${url}`);

    const headerInfo: ParsedReceiptData['headerInfo'] = {
      receipt_date: null,
      total_amount: null,
      store_name: 'ΣΚΛΑΒΕΝΙΤΗΣ', // Derived from hostname
      uid: null,
    };
    const items: ParsedReceiptData['items'] = [];

    // Check if HTML content was provided by the client
    if (!htmlContent) {
        console.error(`${logPrefix}HTML content is required for Epsilon/Sklavenitis parser but was not provided.`);
        // Option 1: Throw an error to mark the job as failed clearly
        throw new Error("HTML content required for this parser was not provided by the client.");
        // Option 2: Return empty data with a warning (less explicit failure)
        // console.warn(`${logPrefix}HTML content not provided. Returning minimal data.`);
        // return { headerInfo, items };
    }

    // --- Parse the PROVIDED HTML using Cheerio ---
    console.log(`${logPrefix}Parsing provided HTML content (length: ${htmlContent.length}) with Cheerio...`);
    const $ = cheerio.load(htmlContent);

    // --- Extract Data using confirmed selectors ---
    // (Keep the extraction logic as determined previously from the rendered HTML)

    // Date
    try {
        const dateElement = $('span#issue-date');
        const dateText = dateElement.text().trim(); // e.g., "22/03/2025 12:30"
        const dateMatch = dateText.match(/(\d{2})\/(\d{2})\/(\d{4})/); // Extract DD/MM/YYYY part
        if (dateMatch && dateMatch.length === 4) {
            const [, day, month, year] = dateMatch;
            const utcDate = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
            if (!isNaN(utcDate.getTime())) {
                headerInfo.receipt_date = utcDate.toISOString();
            } else { console.warn(`${logPrefix}Parsed invalid date components: ${dateMatch[0]}`); }
        } else { console.warn(`${logPrefix}Could not find date match (DD/MM/YYYY) in text: "${dateText}"`); }
    } catch (e) { console.error(`${logPrefix}Error parsing date: ${e instanceof Error ? e.message : String(e)}`); }

    // Total Amount
    try {
        // IMPORTANT: The total is in an INPUT field in the rendered HTML.
        // Cheerio cannot get the '.val()' of an input like jQuery.
        // We need to get the 'value' ATTRIBUTE.
        const totalValue = $('input#gross-value').attr('value'); // Get 'value' attribute
        if (totalValue) {
            headerInfo.total_amount = parseFloat(totalValue) || null;
        } else { console.warn(`${logPrefix}Could not find value attribute for total amount input#gross-value`); }
    } catch (e) { console.error(`${logPrefix}Error parsing total amount: ${e instanceof Error ? e.message : String(e)}`); }

    // UID
    try {
        const uidSpan = $('div.doc-info__container span:contains("UID:")');
        if (uidSpan.length > 0) {
            const fullText = uidSpan.text();
            const uidMatch = fullText.match(/UID:\s*([A-F0-9]+)/i);
            if (uidMatch && uidMatch[1]) {
                 headerInfo.uid = uidMatch[1];
            } else {
                const nextElementText = uidSpan.next().text().trim();
                 if (nextElementText.match(/^[A-F0-9]+$/i)) {
                    headerInfo.uid = nextElementText;
                 } else {
                    console.warn(`${logPrefix}Could not extract UID value from text: "${fullText}" or its sibling.`);
                 }
            }
        } else { console.warn(`${logPrefix}Could not find element containing "UID:" text.`); }
    } catch (e) { console.error(`${logPrefix}Error parsing UID: ${e instanceof Error ? e.message : String(e)}`); }

    // Items
    try {
        const itemsTable = $('div.document-lines-table table.table');
        itemsTable.find('tbody tr').each((index: number, element: cheerio.Element) => {
            const columns = $(element).find('td');
            if (columns.length >= 6) {
                const name = $(columns[1]).text().trim();
                const quantityText = $(columns[2]).text().trim();
                const vatAmountText = $(columns[4]).text().trim();
                const netValueText = $(columns[5]).text().trim();

                const quantity = parseFloat(quantityText.replace(',', '.')) || 0;
                const vatAmount = parseFloat(vatAmountText.replace(',', '.')) || 0;
                const netValue = parseFloat(netValueText.replace(',', '.')) || 0;
                const price = parseFloat((netValue + vatAmount).toFixed(2));

                if (name && quantity > 0 && !isNaN(price)) {
                    items.push({ name, quantity, price });
                } else if (name || quantityText || vatAmountText || netValueText) {
                    console.warn(`${logPrefix}Skipping item row ${index + 1}: Could not parse data reliably (Name: '${name}', Qty: '${quantityText}', VAT: '${vatAmountText}', Net: '${netValueText}')`);
                }
            } else {
                console.warn(`${logPrefix}Skipping item row ${index + 1} due to insufficient columns (${columns.length})`);
            }
        });
    } catch (e) { console.error(`${logPrefix}Error parsing items: ${e instanceof Error ? e.message : String(e)}`); }
    // --- End Data Extraction ---

    console.log(`${logPrefix}Parsed Header:`, headerInfo);
    console.log(`${logPrefix}Parsed ${items.length} items.`);

    if (items.length === 0 && !headerInfo.total_amount && !headerInfo.receipt_date && !headerInfo.uid) {
      console.warn(`${logPrefix}Parsing provided HTML yielded no significant data from ${url}. Check selectors and HTML source.`);
    }

    return { headerInfo, items };
  }
};

export default epsilonSklavenitisParser;
// Remove exported closeBrowser function as Puppeteer is no longer used here
// export { closeBrowserInstance as closeEpsilonSklavenitisBrowser };
