-- Migration script to add Row Level Security (RLS) policies for receipts and receipt_items

-- Policy: Allow authenticated users to select their own receipts
CREATE POLICY "Allow authenticated users to select their own receipts"
ON public.receipts
FOR SELECT
TO authenticated -- Grant permission only to logged-in users
USING (auth.uid() = user_id); -- Condition: user_id column must match the logged-in user's ID

-- Policy: Allow authenticated users to select items belonging to their own receipts
CREATE POLICY "Allow authenticated users to select items from their own receipts"
ON public.receipt_items
FOR SELECT
TO authenticated
USING (
  -- Check if the receipt_id of the item exists in the receipts table
  -- AND the user_id on that receipt matches the logged-in user's ID
  EXISTS (
    SELECT 1
    FROM public.receipts
    WHERE receipts.id = receipt_items.receipt_id AND receipts.user_id = auth.uid()
  )
);

-- Optional: Policies for INSERT/UPDATE/DELETE if needed later
-- CREATE POLICY "Allow authenticated users to insert their own receipts"
-- ON public.receipts
-- FOR INSERT
-- TO authenticated
-- WITH CHECK (auth.uid() = user_id);

-- CREATE POLICY "Allow authenticated users to insert items for their own receipts"
-- ON public.receipt_items
-- FOR INSERT
-- TO authenticated
-- WITH CHECK (
--   EXISTS (
--     SELECT 1
--     FROM public.receipts
--     WHERE receipts.id = receipt_items.receipt_id AND receipts.user_id = auth.uid()
--   )
-- );

-- Remember to grant permissions if not already done
-- GRANT SELECT ON public.receipts TO authenticated;
-- GRANT SELECT ON public.receipt_items TO authenticated;
-- (These were likely granted in the initial migration, but double-check if issues persist)
