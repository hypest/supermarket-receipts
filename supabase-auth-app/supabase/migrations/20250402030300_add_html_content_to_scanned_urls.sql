-- Migration script to add html_content column to scanned_urls table

ALTER TABLE public.scanned_urls
ADD COLUMN html_content text; -- Add column to store extracted HTML

COMMENT ON COLUMN public.scanned_urls.html_content IS 'Stores the full HTML content extracted from the receipt URL by the client application.';
