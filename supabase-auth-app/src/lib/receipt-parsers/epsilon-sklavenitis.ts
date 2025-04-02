// NOTE: This parser now RELIES on the client (e.g., Android WebView)
// to provide the fully rendered HTML content, as direct fetching/Puppeteer
// is blocked by Cloudflare or too slow/unreliable in serverless.

import * as cheerio from 'cheerio';
import { ReceiptParser, ParsedReceiptData } from './types';

// --- NO LONGER USING PUPPETEER IN THIS PARSER ---

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
        throw new Error("HTML content required for this parser was not provided by the client.");
    }

    // --- Parse the PROVIDED HTML using Cheerio ---
    console.log(`${logPrefix}Parsing provided HTML content (length: ${htmlContent.length}) with Cheerio...`);
    const $ = cheerio.load(htmlContent);

    // --- Extract Data using confirmed selectors from cont.html ---

    // Date
    try {
        const dateElement = $('span#issue-date'); // Correct selector
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

    // Total Amount - Using Payment Methods table as input#gross-value might not have value attribute set
    try {
        const paymentTableTotalElement = $('div.form-section__container:has(h6:contains("Τρόποι Πληρωμής")) table tbody td:last-child');
        if (paymentTableTotalElement.length > 0) {
            const totalString = paymentTableTotalElement.first().text().trim(); // e.g., "29,22"
            // Replace comma with dot for parsing
            headerInfo.total_amount = parseFloat(totalString.replace(',', '.')) || null;
        } else {
             console.warn(`${logPrefix}Could not find total amount in payment methods table. Trying input#gross-value attribute...`);
             // Fallback to input attribute just in case
             const totalValueAttr = $('input#gross-value').attr('value');
             if (totalValueAttr) {
                 headerInfo.total_amount = parseFloat(totalValueAttr) || null;
             } else {
                 console.warn(`${logPrefix}Could not find total amount using fallback input#gross-value attribute either.`);
             }
        }
    } catch (e) { console.error(`${logPrefix}Error parsing total amount: ${e instanceof Error ? e.message : String(e)}`); }

    // UID
    try {
        // Find the span containing "UID:", then extract the UID value from its text content
        const uidSpan = $('div.doc-info__container span:contains("UID:")');
        if (uidSpan.length > 0) {
            const fullText = uidSpan.text(); // Text might be "UID: ACTUAL_UID_VALUE"
            const uidMatch = fullText.match(/UID:\s*([A-F0-9]+)/i); // Extract hex value after "UID:"
            if (uidMatch && uidMatch[1]) {
                 headerInfo.uid = uidMatch[1];
            } else {
                 // Fallback if UID is in the next element (less likely based on provided HTML)
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
        const itemsTable = $('div.document-lines-table table.table'); // Correct table selector
        itemsTable.find('tbody tr').each((index: number, element: cheerio.Element) => {
            const columns = $(element).find('td');
            if (columns.length >= 6) { // Need at least 6 columns
                const name = $(columns[1]).text().trim(); // Column index 1 (second td)
                const quantityText = $(columns[2]).text().trim(); // Column index 2 (third td)
                const vatAmountText = $(columns[4]).text().trim(); // Column index 4 (fifth td)
                const netValueText = $(columns[5]).text().trim(); // Column index 5 (sixth td)

                // Use comma as decimal separator for parsing based on example HTML
                const quantity = parseFloat(quantityText.replace(',', '.')) || 0;
                const vatAmount = parseFloat(vatAmountText.replace(',', '.')) || 0;
                const netValue = parseFloat(netValueText.replace(',', '.')) || 0;
                const price = parseFloat((netValue + vatAmount).toFixed(2)); // Calculate total line price

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
// No browser closing function needed anymore
// export { closeBrowserInstance as closeEpsilonSklavenitisBrowser };
