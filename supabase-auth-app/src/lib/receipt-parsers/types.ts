import * as cheerio from 'cheerio';

// Standardized output structure for all parsers
export interface ParsedReceiptData {
  headerInfo: {
    receipt_date?: string | null; // ISO timestamp string
    total_amount?: number | null;
    store_name?: string | null;
    // Add other common header fields if needed
  };
  items: {
    name: string;
    quantity: number;
    price: number; // Total line price
  }[];
}

// Interface for a receipt parser module
export interface ReceiptParser {
  // Takes the URL of the receipt page and returns the parsed data
  // It should handle fetching, iframe detection (if necessary), and parsing
  // Throws an error if parsing fails critically
  parse: (url: string, jobId?: string) => Promise<ParsedReceiptData>;
}
