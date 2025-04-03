-- Migration script to add the UID column and related constraints/indexes to the receipts table

-- Add the uid column
ALTER TABLE public.receipts
ADD COLUMN uid text;

-- Add a UNIQUE constraint to the uid column
-- Note: If the table already contains duplicate NULLs or empty strings in a future uid column,
-- this might fail. Consider data cleanup if necessary before applying in production on existing data.
ALTER TABLE public.receipts
ADD CONSTRAINT receipts_uid_key UNIQUE (uid);

-- Add a comment to the new column
COMMENT ON COLUMN public.receipts.uid IS 'Unique identifier from the government service (e.g., AADE myDATA).';

-- Add an index on the uid column for efficient lookup and duplicate prevention
CREATE INDEX idx_receipts_uid ON public.receipts(uid);
