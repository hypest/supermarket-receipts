ALTER TABLE public.receipt_items
ADD COLUMN unit_price NUMERIC;

-- Optional: Add a comment describing the column
COMMENT ON COLUMN public.receipt_items.unit_price IS 'The price per unit of the item';
