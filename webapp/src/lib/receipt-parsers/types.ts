// Standardized output structure for all parsers
export interface ParsedReceiptData {
  headerInfo: {
    receipt_date?: string | null; // ISO timestamp string
    total_amount?: number | null;
    store_name?: string | null;
    uid?: string | null; // Unique identifier from the government service
    // Add other common header fields if needed
  };
  items: {
    name: string;
    quantity: number;
    price: number; // Total line price
    unit_price?: number | null; // Price per unit (optional)
    vat_percentage?: number | null; // VAT percentage (optional)
  }[];
}

// Interface for a receipt parser module
export interface ReceiptParser {
  // Takes the URL and optionally the pre-fetched HTML content of the receipt page
  // and returns the parsed data.
  // If htmlContent is provided, the parser should use it directly.
  // Otherwise, it should fetch the content from the URL.
  // Throws an error if parsing fails critically.
  parse: (url: string, jobId?: string, htmlContent?: string) => Promise<ParsedReceiptData>;
}
