import axios from 'axios';
import * as cheerio from 'cheerio';
import { ReceiptParser, ParsedReceiptData } from './types';

const entersoftParser: ReceiptParser = {
  // Update signature to accept optional htmlContent
  parse: async (url: string, jobId?: string, htmlContent?: string): Promise<ParsedReceiptData> => {
    const logPrefix = jobId ? `Job ${jobId}: ` : '';
    console.log(`${logPrefix}Using Entersoft parser for URL: ${url}`);

    let initialHtml: string;
    let iframeSrc: string | undefined;
    let iframeUrl: string;
    let receiptHtml: string;

    if (htmlContent) {
        // If HTML is provided, use it directly
        console.log(`${logPrefix}Using provided HTML content (length: ${htmlContent.length})`);
        initialHtml = htmlContent;
        // Attempt to parse iframe from provided HTML
        const $initial = cheerio.load(initialHtml);
        iframeSrc = $initial('iframe#iframeContent').attr('src');
        if (!iframeSrc) {
            // If no iframe in provided HTML, maybe it IS the iframe content?
            // Or maybe the structure is different than expected.
            // For now, assume the provided HTML *might* be the receipt content itself.
            console.warn(`${logPrefix}No iframe found in provided HTML. Assuming it might be the receipt content itself.`);
            receiptHtml = initialHtml; // Use initial HTML as receipt HTML
            // We don't have an iframeUrl in this case, which might affect logging/debugging
            iframeUrl = url; // Use original URL for logging context
        } else {
             // Construct absolute URL and fetch iframe content (still needed if initial HTML had iframe)
            iframeUrl = new URL(iframeSrc, url).toString();
            console.log(`${logPrefix}Found iframe URL in provided HTML: ${iframeUrl}. Fetching content...`);
            const receiptResponse = await axios.get<string>(iframeUrl, {
                headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
                'Referer': url // Use original URL as referer
                },
                timeout: 20000,
                responseType: 'text'
            });
            receiptHtml = receiptResponse.data;
            console.log(`${logPrefix}Fetched receipt HTML (length: ${receiptHtml.length})`);
        }

    } else {
        // If no HTML provided, fetch as before
        // 1. Fetch the initial webpage content to find the iframe
        console.log(`${logPrefix}Fetching initial URL: ${url}`);
        const initialResponse = await axios.get<string>(url, { // Specify expected response type as string
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36' },
      timeout: 15000,
          responseType: 'text'
        });
        initialHtml = initialResponse.data;
        console.log(`${logPrefix}Fetched initial HTML (length: ${initialHtml.length})`);

        // 2. Parse initial HTML to find the iframe source
        const $initial = cheerio.load(initialHtml);
        iframeSrc = $initial('iframe#iframeContent').attr('src');

        if (!iframeSrc) {
          throw new Error(`${logPrefix}Could not find iframe#iframeContent src in initial HTML from ${url}`);
        }

        // 3. Construct absolute URL and fetch iframe content
        iframeUrl = new URL(iframeSrc, url).toString();
        console.log(`${logPrefix}Found iframe URL: ${iframeUrl}. Fetching content...`);
        const receiptResponse = await axios.get<string>(iframeUrl, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Referer': url
          },
          timeout: 20000,
          responseType: 'text'
        });
        receiptHtml = receiptResponse.data;
        console.log(`${logPrefix}Fetched receipt HTML (length: ${receiptHtml.length})`);
    }


    // 4. Parse the final receipt HTML (either fetched or provided)
    const $ = cheerio.load(receiptHtml);
    const headerInfo: ParsedReceiptData['headerInfo'] = {};
    const items: ParsedReceiptData['items'] = [];

    // Extract header info
    headerInfo.store_name = $('div.BoldBlueHeader.fontSize12pt').first().text().trim() || null;
    const dateElementText = $('div.fontSize8pt:contains("Ημ/νία έκδοσης:")').text();
    const dateMatch = dateElementText.match(/(\d{2}-\d{2}-\d{4})/);
    if (dateMatch && dateMatch[1]) {
      const [day, month, year] = dateMatch[1].split('-');
      try {
        // Create date in UTC to avoid timezone issues during conversion
        const utcDate = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
        if (!isNaN(utcDate.getTime())) {
             headerInfo.receipt_date = utcDate.toISOString();
        } else {
             throw new Error('Invalid date components');
        }
      } catch (dateError) {
        console.warn(`${logPrefix}Could not parse date string "${dateMatch[1]}": ${(dateError as Error).message}`);
        headerInfo.receipt_date = null;
      }
    } else {
      headerInfo.receipt_date = null;
    }
    const totalElement = $('div:contains("ΤΕΛΙΚΗ ΑΞΙΑ")').parent().find('div.backgrey');
    const totalString = totalElement.text().trim();
    const totalMatch = totalString.match(/([\d.,]+)/);
    if (totalMatch && totalMatch[1]) {
      headerInfo.total_amount = parseFloat(totalMatch[1].replace('.', '').replace(',', '.')) || null;
    } else {
      headerInfo.total_amount = null;
    }

    // Extract UID (Unique Identifier) - More specific selector based on provided HTML
    const uidLabelDiv = $('div.col.fontSize8pt.mr-0.pr-0:contains("UID:")');
    let uidValue: string | null = null;
    if (uidLabelDiv.length > 0) {
        // Find the specific sibling div containing the value
        const uidValueDiv = uidLabelDiv.siblings('div.col-8.fontSize8pt.ml-0.pl-0');
        if (uidValueDiv.length > 0) {
            uidValue = uidValueDiv.text().trim() || null;
            console.log(`${logPrefix}Found UID using specific selector.`);
        }
    }

    // Fallback to the previous, slightly more general selector if the specific one fails
    if (!uidValue) {
        console.log(`${logPrefix}Specific UID selector failed, trying fallback...`);
        let uidElement = $('div:contains("UID:")').next('div'); // Check for "UID:" label
        if (uidElement.length === 0) {
          // If not found, check for Greek label "Αρ. Σήμανσης:"
          uidElement = $('div:contains("Αρ. Σήμανσης:")').next('div');
        }
        if (uidElement.length > 0) {
            uidValue = uidElement.text().trim() || null;
            console.log(`${logPrefix}Found UID using fallback selector.`);
        }
    }

    if (uidValue) {
        headerInfo.uid = uidValue;
    } else {
        console.warn(`${logPrefix}Could not find UID element using specific or fallback patterns.`);
        headerInfo.uid = null;
    }


    console.log(`${logPrefix}Extracted Header Info:`, headerInfo);

    // Extract items
    $('#no-more-tables table tbody tr').each((index: number, element: cheerio.Element) => {
      if ($(element).hasClass('comments')) return;

      // Find columns by data-title attribute
      const nameElement = $(element).find('td[data-title="Περιγραφή"]');
      const quantityElement = $(element).find('td[data-title="Ποσότητα"]');
      const priceElement = $(element).find('td[data-title="Συνολική Αξία"]');

      // Check if all required elements were found
      if (!nameElement.length || !quantityElement.length || !priceElement.length) {
        console.warn(`${logPrefix}Skipping row ${index + 1}: Could not find all required cells (name, quantity, price) using data-title.`);
        return;
      }

      const name = nameElement.text().trim();
      const quantityText = quantityElement.text().trim();
      const priceText = priceElement.text().trim();

      const quantity = parseFloat(quantityText.replace('.', '').replace(',', '.')) || 0;
      const price = parseFloat(priceText.replace('.', '').replace(',', '.')) || 0;

      if (name && quantity > 0) {
        items.push({ name, quantity, price });
      } else if (name || quantityText || priceText) {
         // Only warn if some data was present but couldn't be fully parsed
        console.warn(`${logPrefix}Skipping row ${index + 1}: Could not parse data reliably (Name: '${name}', Qty Text: '${quantityText}', Price Text: '${priceText}')`);
      }
    });
    console.log(`${logPrefix}Parsed ${items.length} items.`);

    if (items.length === 0) {
      // Decide if this is an error. Maybe some receipts genuinely have 0 items?
      // For now, let's treat it as a potential issue but return empty data.
      console.warn(`${logPrefix}No items parsed from iframe content ${iframeUrl}.`);
      // Consider throwing an error if 0 items is always invalid:
      // throw new Error(`${logPrefix}No items found or parsed from the iframe content.`);
    }

    return { headerInfo, items };
  }
};

export default entersoftParser;
