import puppeteer from 'puppeteer-extra'; // Use puppeteer-extra
import StealthPlugin from 'puppeteer-extra-plugin-stealth'; // Import stealth plugin
// Import Browser and Page types explicitly from puppeteer-core
import { Browser, Page } from 'puppeteer-core';
import chromium from '@sparticuz/chromium'; // Import chromium for serverless
import * as cheerio from 'cheerio';
import { ReceiptParser, ParsedReceiptData } from './types';

// Apply the stealth plugin
puppeteer.use(StealthPlugin());

// --- Puppeteer Browser Management ---
// Explicitly type browserInstance as Browser | null
let browserInstance: Browser | null = null;
// Add explicit return type Promise<Browser>
async function getBrowser(): Promise<Browser> {
  // Check if browserInstance exists and is connected
  const isConnected = browserInstance && browserInstance.isConnected();
  if (!browserInstance || !isConnected) {
    if (browserInstance && !isConnected) {
        console.log('Puppeteer browser instance disconnected. Closing...');
        await closeBrowserInstance(); // Attempt to close cleanly before relaunching
    }

    if (process.env.VERCEL) {
      // Vercel environment: Use @sparticuz/chromium
      console.log('Launching Puppeteer-extra browser instance for Vercel...');
      const executablePath = await chromium.executablePath();
      console.log(`Using Chromium executable path: ${executablePath || 'default'}`);
      browserInstance = await puppeteer.launch({ // puppeteer here is puppeteer-extra
        args: chromium.args,
        defaultViewport: chromium.defaultViewport,
        executablePath: executablePath,
        headless: chromium.headless,
      });
      console.log('Puppeteer-extra browser launched with @sparticuz/chromium.');
    } else {
      // Local environment: Use standard puppeteer-core launch, specifying the Chrome channel
      console.log('Launching Puppeteer-extra browser instance locally (using Chrome channel)...');
      // Note: Ensure you have Google Chrome installed locally for this to work
      browserInstance = await puppeteer.launch({ // puppeteer here is puppeteer-extra
        channel: 'chrome', // Tell puppeteer-core to find local Chrome
        headless: true, // Or false for debugging visually
        args: ['--no-sandbox', '--disable-setuid-sandbox'] // Standard args
      });
      console.log('Puppeteer-extra browser launched locally.');
    }
  }
  // Add a check to satisfy TypeScript's strict null checks
  if (!browserInstance) {
      throw new Error("Failed to launch or retrieve browser instance.");
  }
  return browserInstance;
}

async function closeBrowserInstance(): Promise<void> {
    if (browserInstance) {
        const instanceToClose = browserInstance; // Capture instance
        browserInstance = null; // Set to null immediately
        console.log('Closing Puppeteer browser instance...');
        try {
            await instanceToClose.close();
            console.log('Puppeteer browser closed.');
        } catch (closeError) {
             console.error(`Error closing browser instance: ${closeError instanceof Error ? closeError.message : String(closeError)}`);
        }
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
    console.log(`${logPrefix}Using Epsilon/Sklavenitis Puppeteer-extra parser for URL: ${url}`);

    const headerInfo: ParsedReceiptData['headerInfo'] = {
      receipt_date: null,
      total_amount: null,
      store_name: 'ΣΚΛΑΒΕΝΙΤΗΣ', // Derived from hostname
      uid: null,
    };
    const items: ParsedReceiptData['items'] = [];
    let page: Page | null = null;
    let pageContent: string = ''; // Initialize to empty string
    let browser: Browser | null = null;
    let isChallengeDetected = false; // Flag to track if challenge was detected

    try {
      browser = await getBrowser();
      page = await browser.newPage();
      await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36');
      await page.setViewport({ width: 1280, height: 800 });

      console.log(`${logPrefix}Navigating to ${url}...`);
      await page.goto(url, { waitUntil: 'networkidle2', timeout: 60000 });
      console.log(`${logPrefix}Initial page navigation complete.`);

      // --- Check for Cloudflare challenge ---
      console.log(`${logPrefix}Checking for Cloudflare challenge (15s timeout)...`);
      const challengeIframeSelector = 'iframe[src*="challenges.cloudflare.com"]';
      try {
          await page.waitForSelector(challengeIframeSelector, { timeout: 15000, visible: true });
          console.warn(`${logPrefix}Cloudflare challenge detected (iframe found). Skipping detailed parsing.`);
          isChallengeDetected = true;
      } catch (timeoutError) {
          // Timeout means the challenge iframe likely didn't appear quickly
          console.log(`${logPrefix}No Cloudflare challenge iframe detected quickly.`);
          isChallengeDetected = false;
      }
      // --- End Challenge Check ---

      // Only wait for receipt content if no challenge was detected
      if (!isChallengeDetected) {
          const renderedContentSelector = 'div.doc-info__container';
          console.log(`${logPrefix}Waiting for function to confirm selector "${renderedContentSelector}" exists (up to 80s)...`);
          try {
            await page.waitForFunction(
              (selector) => document.querySelector(selector) !== null,
              { timeout: 80000 },
              renderedContentSelector
            );
            console.log(`${logPrefix}Rendered content confirmed via function.`);
          } catch (waitError: unknown) {
            const errorMsg = waitError instanceof Error ? waitError.message : String(waitError);
            console.warn(`${logPrefix}Timeout or error waiting for selector "${renderedContentSelector}": ${errorMsg}. Page might not have rendered correctly or selector is wrong. Trying to get content anyway...`);
            const screenshotPath = `/tmp/error_screenshot_${jobId || Date.now()}.png`;
            try {
                if (page) {
                     await page.screenshot({ path: screenshotPath, fullPage: true });
                     console.log(`${logPrefix}Screenshot saved to ${screenshotPath}`);
                }
            } catch (ssError) { console.error(`${logPrefix}Failed to take screenshot: ${ssError instanceof Error ? ssError.message : String(ssError)}`); }
          }

          // Extract content only if page exists
          if (page) {
            console.log(`${logPrefix}Extracting rendered HTML content...`);
            pageContent = await page.content();
            console.log(`${logPrefix}Extracted HTML content (length: ${pageContent?.length ?? 0})`);
          } else {
             console.warn(`${logPrefix}Page object was unexpectedly null, cannot extract content.`);
             pageContent = ''; // Ensure pageContent is string
          }
      } else {
          // If challenge detected, ensure pageContent remains empty
          pageContent = '';
          console.log(`${logPrefix}Skipping content extraction due to Cloudflare challenge.`);
      }

    } catch (error: unknown) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      console.error(`${logPrefix}Error during Puppeteer navigation/rendering for ${url}: ${errorMsg}`, error);
      if (page) {
          try { await page.close(); } catch { /* ignore */ }
      }
      // Don't close the shared browser instance on error here
      throw new Error(`Puppeteer failed for Epsilon/Sklavenitis URL: ${errorMsg}`);
    } finally {
      if (page) {
        try {
            await page.close();
            console.log(`${logPrefix}Puppeteer page closed.`);
        } catch (_closeError) {
            console.error(`${logPrefix}Error closing Puppeteer page: ${_closeError instanceof Error ? _closeError.message : String(_closeError)}`);
        }
      }
    }

    // Only proceed with parsing if content was retrieved AND no challenge was detected
    if (!pageContent || isChallengeDetected) {
      console.warn(`${logPrefix}Failed to retrieve page content or Cloudflare challenge detected. Returning minimal data.`);
       return { headerInfo, items }; // Return minimal data
    }

    // --- Parse the RENDERED HTML using Cheerio ---
    console.log(`${logPrefix}Parsing rendered HTML with Cheerio...`);
    const $ = cheerio.load(pageContent);

    // --- Extract Data using confirmed selectors ---
    // Date
    try {
        const dateElement = $('span#issue-date');
        const dateText = dateElement.text().trim();
        const dateMatch = dateText.match(/(\d{2})\/(\d{2})\/(\d{4})/);
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
        const totalValue = $('input#gross-value').val();
        if (totalValue) {
            headerInfo.total_amount = parseFloat(totalValue) || null;
        } else { console.warn(`${logPrefix}Could not find value for total amount input#gross-value`); }
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
      console.warn(`${logPrefix}Puppeteer parsing yielded no significant data from ${url}. Check selectors and page rendering.`);
    }

    return { headerInfo, items };
  }
};

export default epsilonSklavenitisParser;
// Export closeBrowser function if needed elsewhere (e.g., for graceful shutdown in main app)
export { closeBrowserInstance as closeEpsilonSklavenitisBrowser };
