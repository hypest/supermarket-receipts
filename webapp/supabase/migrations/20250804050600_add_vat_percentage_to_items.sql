ALTER TABLE public.receipt_items
ADD COLUMN vat_percentage NUMERIC;

-- Optional: Add a comment describing the column
COMMENT ON COLUMN public.receipt_items.vat_percentage IS 'The VAT percentage applied to the item';
