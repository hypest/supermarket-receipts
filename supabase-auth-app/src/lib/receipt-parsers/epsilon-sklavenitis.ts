import puppeteer, { Browser, Page } from 'puppeteer-core'; // Use puppeteer-core
import chromium from '@sparticuz/chromium'; // Import chromium for serverless
import * as cheerio from 'cheerio';
import { ReceiptParser, ParsedReceiptData } from './types';

// --- Puppeteer Browser Management ---
let browserInstance: Browser | null = null;
async function getBrowser(): Promise<Browser> {
  if (!browserInstance || !browserInstance.isConnected()) {
    console.log('Launching new Puppeteer browser instance for serverless...');
    const executablePath = await chromium.executablePath();
    console.log(`Using Chromium executable path: ${executablePath}`);
    // Use @sparticuz/chromium for executable path and args
    browserInstance = await puppeteer.launch({
      args: chromium.args,
      defaultViewport: chromium.defaultViewport,
      executablePath: executablePath,
      headless: chromium.headless, // Use chromium.headless value
      // ignoreHTTPSErrors: true, // This option belongs in page.goto, not launch
    });
    console.log('Puppeteer browser launched with @sparticuz/chromium.');
  }
  return browserInstance;
}

async function closeBrowserInstance(): Promise<void> {
    if (browserInstance) {
        console.log('Closing Puppeteer browser instance...');
        await browserInstance.close();
        browserInstance = null;
        console.log('Puppeteer browser closed.');
    }
}

// Ensure browser is closed on exit signals
process.on('SIGINT', closeBrowserInstance);
process.on('SIGTERM', closeBrowserInstance);
process.on('exit', closeBrowserInstance);
// --- End Puppeteer Management ---


// --- Parser Implementation ---
const epsilonSklavenitisParser: ReceiptParser = {
  parse: async (url: string, jobId?: string): Promise<ParsedReceiptData> => {
    const logPrefix = jobId ? `Job ${jobId}: ` : '';
    console.log(`${logPrefix}Using Epsilon/Sklavenitis Puppeteer parser for URL: ${url}`);

    const headerInfo: ParsedReceiptData['headerInfo'] = {
      receipt_date: null,
      total_amount: null,
      store_name: 'ΣΚΛΑΒΕΝΙΤΗΣ', // Derived from hostname
      uid: null,
    };
    const items: ParsedReceiptData['items'] = [];
    let page: Page | null = null; // Use Page type from puppeteer
    let pageContent: string | null = null;

    try {
      const browser = await getBrowser();
      page = await browser.newPage();
      await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36');
      await page.setViewport({ width: 1280, height: 800 }); // Set a reasonable viewport

      console.log(`${logPrefix}Navigating to ${url}...`);
      // Change waitUntil to 'domcontentloaded' - might be more reliable in serverless
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 });
      console.log(`${logPrefix}Initial page navigation complete (DOM loaded).`);

      // Wait for the container with final details (UID/MARK) to ensure rendering
      const renderedContentSelector = 'div.doc-info__container'; // Confirmed selector
      console.log(`${logPrefix}Waiting for selector "${renderedContentSelector}" to appear...`);
      try {
        await page.waitForSelector(renderedContentSelector, { timeout: 35000 });
        console.log(`${logPrefix}Rendered content selector found.`);
      } catch (waitError: unknown) { // Type the error
        // Use the waitError variable in the warning message
        const errorMsg = waitError instanceof Error ? waitError.message : String(waitError);
        console.warn(`${logPrefix}Timeout or error waiting for selector "${renderedContentSelector}": ${errorMsg}. Page might not have rendered correctly or selector is wrong. Trying to get content anyway...`);
        // Optionally capture a screenshot for debugging
        try { await page.screenshot({ path: `error_screenshot_${jobId || 'test'}.png`, fullPage: true }); } catch (ssError) { console.error("Failed to take screenshot:", ssError); }
      }

      console.log(`${logPrefix}Extracting rendered HTML content...`);
      pageContent = await page.content();
      console.log(`${logPrefix}Extracted HTML content (length: ${pageContent?.length ?? 0})`);

    } catch (error: unknown) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      console.error(`${logPrefix}Error during Puppeteer navigation/rendering for ${url}: ${errorMsg}`, error); // Use the error variable
      // Attempt to close browser even if page navigation failed
      if (page) {
          try { await page.close(); } catch { /* ignore closing error if main error occurred */ }
      }
      await closeBrowserInstance(); // Ensure browser is closed on error
      throw new Error(`Puppeteer failed for Epsilon/Sklavenitis URL: ${errorMsg}`);
    } finally {
      if (page) {
        try {
            await page.close();
            console.log(`${logPrefix}Puppeteer page closed.`);
        } catch (_closeError) { // Prefix and use the variable
            console.error(`${logPrefix}Error closing Puppeteer page: ${_closeError instanceof Error ? _closeError.message : String(_closeError)}`, _closeError);
        }
      }
      // Note: Browser instance is kept open for potential reuse by subsequent jobs unless an error occurred
    }

    if (!pageContent) {
      // This might happen if the waitForSelector timed out and we didn't throw, but content() failed.
      console.warn(`${logPrefix}Failed to retrieve page content using Puppeteer, possibly due to rendering timeout.`);
      // Return minimal data or throw, depending on desired behavior
       return { headerInfo, items }; // Return minimal data
      // throw new Error(`${logPrefix}Failed to retrieve page content using Puppeteer.`);
    }

    // --- Parse the RENDERED HTML using Cheerio ---
    console.log(`${logPrefix}Parsing rendered HTML with Cheerio...`);
    const $ = cheerio.load(pageContent);

    // --- Extract Data using confirmed selectors ---

    // Date
    try {
        const dateElement = $('span#issue-date');
        const dateText = dateElement.text().trim(); // e.g., "22/03/2025 12:30"
        const dateMatch = dateText.match(/(\d{2})\/(\d{2})\/(\d{4})/); // Extract DD/MM/YYYY part
        if (dateMatch && dateMatch.length === 4) {
            // Destructure only the needed parts (day, month, year), ignoring the full match at index 0
            const [, day, month, year] = dateMatch;
            // Note: Month is 0-indexed in JS Date, so subtract 1
            const utcDate = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
            if (!isNaN(utcDate.getTime())) {
                headerInfo.receipt_date = utcDate.toISOString();
            } else { console.warn(`${logPrefix}Parsed invalid date components: ${dateMatch[0]}`); }
        } else { console.warn(`${logPrefix}Could not find date match (DD/MM/YYYY) in text: "${dateText}"`); }
    } catch (e) { console.error(`${logPrefix}Error parsing date: ${e instanceof Error ? e.message : String(e)}`); }

    // Total Amount
    try {
        const totalValue = $('input#gross-value').val(); // Get value attribute
        if (totalValue) {
            // Assuming value is like "29.22" (using dot as decimal separator)
            headerInfo.total_amount = parseFloat(totalValue) || null;
        } else { console.warn(`${logPrefix}Could not find value for total amount input#gross-value`); }
    } catch (e) { console.error(`${logPrefix}Error parsing total amount: ${e instanceof Error ? e.message : String(e)}`); }

    // UID
    try {
        const uidSpan = $('div.doc-info__container span:contains("UID:")');
        if (uidSpan.length > 0) {
            // Get the text of the span containing "UID:", then extract the actual UID value
            // Assuming the format is "UID: ACTUAL_UID_VALUE" possibly on the same line or next
            const fullText = uidSpan.text();
            const uidMatch = fullText.match(/UID:\s*([A-F0-9]+)/i); // Match "UID:" followed by hex chars
            if (uidMatch && uidMatch[1]) {
                 headerInfo.uid = uidMatch[1];
            } else {
                // Fallback: Check the next sibling element if UID wasn't in the same span
                const nextElementText = uidSpan.next().text().trim();
                 if (nextElementText.match(/^[A-F0-9]+$/i)) { // Check if it looks like a hex UID
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
            if (columns.length >= 6) { // Need at least 6 columns for Name, Qty, VAT, Net
                const name = $(columns[1]).text().trim(); // Index 1
                const quantityText = $(columns[2]).text().trim(); // Index 2
                const vatAmountText = $(columns[4]).text().trim(); // Index 4
                const netValueText = $(columns[5]).text().trim(); // Index 5

                const quantity = parseFloat(quantityText.replace(',', '.')) || 0; // Handle potential comma decimal
                const vatAmount = parseFloat(vatAmountText.replace(',', '.')) || 0;
                const netValue = parseFloat(netValueText.replace(',', '.')) || 0;

                // Calculate total line price
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
      console.warn(`${logPrefix}Puppeteer parsing yielded no significant data from ${url}. Check selectors and page rendering.`);
      // Consider if this should be an error or just return empty data
    }

    return { headerInfo, items };
  }
};

export default epsilonSklavenitisParser;
// Export closeBrowser function if needed elsewhere (e.g., for graceful shutdown in main app)
export { closeBrowserInstance as closeEpsilonSklavenitisBrowser };
