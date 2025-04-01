// Simple script to test a specific parser locally
import epsilonSklavenitisParser, { closeEpsilonSklavenitisBrowser } from './src/lib/receipt-parsers/epsilon-sklavenitis'; // Remove extension
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
    // Ensure the browser instance is closed after the test
    await closeEpsilonSklavenitisBrowser();
  }
}

runTest();
