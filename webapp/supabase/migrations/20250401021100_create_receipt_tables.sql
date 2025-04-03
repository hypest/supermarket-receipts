-- Migration script to create tables for receipt processing

-- Table to track the processing status of scanned URLs
CREATE TABLE public.receipt_processing_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scanned_url_id bigint NOT NULL UNIQUE REFERENCES public.scanned_urls(id) ON DELETE CASCADE,
    status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    attempts integer NOT NULL DEFAULT 0,
    last_attempted_at timestamp with time zone,
    error_message text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Add comments to clarify columns
COMMENT ON TABLE public.receipt_processing_jobs IS 'Tracks the processing state for each scanned URL.';
COMMENT ON COLUMN public.receipt_processing_jobs.scanned_url_id IS 'Foreign key linking to the original scanned URL entry.';
COMMENT ON COLUMN public.receipt_processing_jobs.status IS 'Current processing status (pending, processing, completed, failed).';
COMMENT ON COLUMN public.receipt_processing_jobs.attempts IS 'Number of times processing has been attempted.';
COMMENT ON COLUMN public.receipt_processing_jobs.last_attempted_at IS 'Timestamp of the last processing attempt.';
COMMENT ON COLUMN public.receipt_processing_jobs.error_message IS 'Stores error details if processing failed.';

-- Add index on status for potentially querying failed/pending jobs
CREATE INDEX idx_receipt_processing_jobs_status ON public.receipt_processing_jobs(status);

-- Add trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_receipt_processing_jobs_updated_at
BEFORE UPDATE ON public.receipt_processing_jobs
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


-- Table to store header information of successfully processed receipts
CREATE TABLE public.receipts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scanned_url_id bigint NOT NULL UNIQUE REFERENCES public.scanned_urls(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    receipt_date timestamp with time zone,
    total_amount numeric(10, 2), -- Adjust precision and scale as needed
    store_name text,
    processed_at timestamp with time zone NOT NULL DEFAULT now(),
    created_at timestamp with time zone NOT NULL DEFAULT now() -- Keep created_at for consistency
);

-- Add comments
COMMENT ON TABLE public.receipts IS 'Stores header information for successfully processed receipts.';
COMMENT ON COLUMN public.receipts.scanned_url_id IS 'Foreign key linking to the original scanned URL entry.';
COMMENT ON COLUMN public.receipts.user_id IS 'Foreign key linking to the user who scanned the receipt.';
COMMENT ON COLUMN public.receipts.receipt_date IS 'Date extracted from the receipt, if available.';
COMMENT ON COLUMN public.receipts.total_amount IS 'Total amount extracted from the receipt, if available.';
COMMENT ON COLUMN public.receipts.store_name IS 'Store name extracted from the receipt, if available.';
COMMENT ON COLUMN public.receipts.processed_at IS 'Timestamp when the receipt processing was completed.';

-- Add index on user_id for efficient querying by user
CREATE INDEX idx_receipts_user_id ON public.receipts(user_id);


-- Table to store individual line items from processed receipts
CREATE TABLE public.receipt_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id uuid NOT NULL REFERENCES public.receipts(id) ON DELETE CASCADE,
    name text NOT NULL,
    quantity numeric NOT NULL,
    price numeric(10, 2) NOT NULL, -- Assuming this is the total price for the line item (quantity * unit price)
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Add comments
COMMENT ON TABLE public.receipt_items IS 'Stores individual line items for a processed receipt.';
COMMENT ON COLUMN public.receipt_items.receipt_id IS 'Foreign key linking to the parent receipt entry.';
COMMENT ON COLUMN public.receipt_items.name IS 'Name or description of the purchased item.';
COMMENT ON COLUMN public.receipt_items.quantity IS 'Quantity of the item purchased.';
COMMENT ON COLUMN public.receipt_items.price IS 'Total price for this line item (quantity * unit price).';

-- Add index on receipt_id for efficient querying of items for a specific receipt
CREATE INDEX idx_receipt_items_receipt_id ON public.receipt_items(receipt_id);

-- Grant usage permissions if necessary (adjust schema/roles as needed)
GRANT USAGE ON SCHEMA public TO postgres, anon, authenticated, service_role;
GRANT ALL ON TABLE public.receipt_processing_jobs TO postgres, anon, authenticated, service_role;
GRANT ALL ON TABLE public.receipts TO postgres, anon, authenticated, service_role;
GRANT ALL ON TABLE public.receipt_items TO postgres, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_updated_at_column() TO postgres, anon, authenticated, service_role;

-- Enable Row Level Security (RLS) - IMPORTANT for multi-user apps
-- You'll need to define policies later based on your access control needs
ALTER TABLE public.receipt_processing_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receipt_items ENABLE ROW LEVEL SECURITY;

-- Example RLS policy (adjust as needed): Allow users to see their own receipts/items
-- CREATE POLICY "Allow users to select their own receipts" ON public.receipts
-- FOR SELECT USING (auth.uid() = user_id);

-- CREATE POLICY "Allow users to select items from their own receipts" ON public.receipt_items
-- FOR SELECT USING (EXISTS (SELECT 1 FROM public.receipts WHERE receipts.id = receipt_items.receipt_id AND receipts.user_id = auth.uid()));

-- Note: RLS for receipt_processing_jobs might be more complex or handled by service_role only.
