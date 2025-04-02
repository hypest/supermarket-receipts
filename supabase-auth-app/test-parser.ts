// Simple script to test a specific parser locally
import epsilonSklavenitisParser from './src/lib/receipt-parsers/epsilon-sklavenitis'; // Remove extension, remove closeBrowser import
import { ParsedReceiptData } from './src/lib/receipt-parsers/types'; // Remove extension

const testUrl = 'https://epsilondigital-sklavenitis.epsilonnet.gr/FileDocument/Get/c75d537f-be8f-4118-2aa3-08dd626fe4d1';
const testJobId = 'local-test-sklavenitis';

async function runTest() {
  console.log(`Testing parser for URL: ${testUrl}`);
  let result: ParsedReceiptData | null = null;
  try {
    result = await epsilonSklavenitisParser.parse(testUrl, testJobId);
    console.log('\n--- PARSED RESULT ---');
    console.log(JSON.stringify(result, null, 2));
    console.log('---------------------\n');
  } catch (error) {
    console.error('\n--- PARSER FAILED ---');
    console.error(error);
    console.log('---------------------\n');
  } finally {
    // No browser to close for this parser anymore
    console.log('Test finished.');
  }
}

runTest();
