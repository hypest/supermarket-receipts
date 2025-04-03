-- Allow authenticated users to delete their own receipts
CREATE POLICY "Allow authenticated users to delete their own receipts"
ON public.receipts
FOR DELETE
TO authenticated
USING (auth.uid() = user_id);
