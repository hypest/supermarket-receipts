import axios from 'axios';
import * as cheerio from 'cheerio';
import { ReceiptParser, ParsedReceiptData } from './types';

const entersoftParser: ReceiptParser = {
  parse: async (url: string, jobId?: string): Promise<ParsedReceiptData> => {
    const logPrefix = jobId ? `Job ${jobId}: ` : '';
    console.log(`${logPrefix}Using Entersoft parser for URL: ${url}`);

    // 1. Fetch the initial webpage content to find the iframe
    console.log(`${logPrefix}Fetching initial URL: ${url}`);
    const initialResponse = await axios.get(url, {
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36' },
      timeout: 15000
    });
    const initialHtml = initialResponse.data;
    console.log(`${logPrefix}Fetched initial HTML (length: ${initialHtml.length})`);

    // 2. Parse initial HTML to find the iframe source
    const $initial = cheerio.load(initialHtml);
    const iframeSrc = $initial('iframe#iframeContent').attr('src');

    if (!iframeSrc) {
      throw new Error(`${logPrefix}Could not find iframe#iframeContent src in initial HTML from ${url}`);
    }

    // 3. Construct absolute URL and fetch iframe content
    const iframeUrl = new URL(iframeSrc, url).toString();
    console.log(`${logPrefix}Found iframe URL: ${iframeUrl}. Fetching content...`);
    const receiptResponse = await axios.get(iframeUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Referer': url
      },
      timeout: 20000
    });
    const receiptHtml = receiptResponse.data;
    console.log(`${logPrefix}Fetched receipt HTML (length: ${receiptHtml.length})`);

    // 4. Parse the receipt HTML
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
    console.log(`${logPrefix}Extracted Header Info:`, headerInfo);

    // Extract items
    $('#no-more-tables table tbody tr').each((index, element) => {
      if ($(element).hasClass('comments')) return;

      const columns = $(element).find('td');
      if (columns.length < 9) {
        console.warn(`${logPrefix}Skipping row ${index + 1} due to insufficient columns (${columns.length})`);
        return;
      }

      const name = $(columns[1]).text().trim();
      const quantityText = $(columns[3]).text().trim();
      const priceText = $(columns[8]).text().trim();

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
