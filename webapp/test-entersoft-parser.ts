// Simple script to test the Entersoft parser locally using pre-saved HTML
// Simple script to test a specific parser locally using pre-saved HTML
import fs from 'fs/promises'; // Use promises version of fs
import path from 'path';
import entersoftParser from './src/lib/receipt-parsers/entersoft';
import { ParsedReceiptData } from './src/lib/receipt-parsers/types';

// Placeholder URL - might be needed for context, but fetching is bypassed
const testUrl = 'https://e-invoicing.gr/edocuments/ViewInvoice?contentType=PEPPOL&id=44FDB4E3E2F5F8910BDD043518F98712B0726F34&source=A'; // Replace with a real example URL if available/needed
const testJobId = 'local-test-html-entersoft';
// Path to the example HTML file, relative to the webapp directory
const htmlFilePath = path.join(__dirname, 'entersoft-masoutis-example.html'); // This line remains the same, but __dirname is now defined correctly

async function runTest() {
  console.log(`Testing Entersoft parser for URL: ${testUrl} using HTML from ${htmlFilePath}`);
  let result: ParsedReceiptData | null = null;
  try {
    // Read the HTML content from the file
    const htmlContent = await fs.readFile(htmlFilePath, 'utf-8');
    console.log(`Read HTML content (length: ${htmlContent.length})`);

    // Call the parser, providing the HTML content
    // The URL is passed for context, but the parser should use htmlContent
    result = await entersoftParser.parse(testUrl, testJobId, htmlContent);

    console.log('\n--- PARSED RESULT (Entersoft) ---');
    console.log(JSON.stringify(result, null, 2));
    console.log('-----------------------------------\n');
  } catch (error) {
    console.error('\n--- PARSER FAILED (Entersoft) ---');
    console.error(error);
    console.log('-----------------------------------\n');
  } finally {
    console.log('Entersoft test finished.');
  }
}

runTest();
