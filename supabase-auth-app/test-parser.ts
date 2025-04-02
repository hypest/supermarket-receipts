// Simple script to test a specific parser locally using pre-saved HTML
import fs from 'fs/promises'; // Use promises version of fs
import path from 'path';
import epsilonSklavenitisParser from './src/lib/receipt-parsers/epsilon-sklavenitis';
import { ParsedReceiptData } from './src/lib/receipt-parsers/types';

// URL is still needed for context (like deriving store name if needed)
const testUrl = 'https://epsilondigital-sklavenitis.epsilonnet.gr/FileDocument/Get/c75d537f-be8f-4118-2aa3-08dd626fe4d1';
const testJobId = 'local-test-html-sklavenitis';
const htmlFilePath = path.join(__dirname, '../cont.html'); // Path relative to supabase-auth-app dir

async function runTest() {
  console.log(`Testing parser for URL: ${testUrl} using HTML from ${htmlFilePath}`);
  let result: ParsedReceiptData | null = null;
  try {
    // Read the HTML content from the file
    const htmlContent = await fs.readFile(htmlFilePath, 'utf-8');
    console.log(`Read HTML content (length: ${htmlContent.length})`);

    // Call the parser, providing the HTML content
    result = await epsilonSklavenitisParser.parse(testUrl, testJobId, htmlContent);

    console.log('\n--- PARSED RESULT ---');
    console.log(JSON.stringify(result, null, 2));
    console.log('---------------------\n');
  } catch (error) {
    console.error('\n--- PARSER FAILED ---');
    console.error(error);
    console.log('---------------------\n');
  } finally {
    // No browser to close anymore
    console.log('Test finished.');
  }
}

runTest();
