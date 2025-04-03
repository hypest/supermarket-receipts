-- Set REPLICA IDENTITY to FULL for the receipts table
-- This ensures all columns are available during replication,
-- allowing RLS policies based on non-primary key columns (like user_id)
-- to work correctly for DELETE events in Realtime.
ALTER TABLE public.receipts REPLICA IDENTITY FULL;
