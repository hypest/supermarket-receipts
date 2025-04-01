import { ReceiptParser } from './types';
import entersoftParser from './entersoft';
// Import other parsers here as they are created
// import anotherProviderParser from './anotherProvider';

// Map hostnames (or parts of hostnames) to parser modules
const parserRegistry: { [key: string]: ReceiptParser } = {
  'e-invoicing.gr': entersoftParser,
  // 'www.anotherprovider.com': anotherProviderParser,
};

/**
 * Finds the appropriate receipt parser based on the URL's hostname.
 * @param url The URL of the receipt page.
 * @returns The corresponding ReceiptParser module or null if no match is found.
 */
export function getParserForUrl(url: string): ReceiptParser | null {
  try {
    const hostname = new URL(url).hostname;

    // Find a matching parser in the registry
    for (const key in parserRegistry) {
      // Use endsWith for flexibility (e.g., match 'subdomain.e-invoicing.gr')
      // Or use exact match if needed: if (hostname === key)
      if (hostname.endsWith(key)) {
        console.log(`Found parser for hostname: ${hostname} (matched key: ${key})`);
        return parserRegistry[key];
      }
    }

    console.warn(`No parser found for hostname: ${hostname}`);
    return null;
  } catch (error) {
    console.error(`Error determining parser for URL "${url}":`, error);
    return null;
  }
}
