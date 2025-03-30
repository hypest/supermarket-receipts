'use client';

import { useEffect, useRef, useState } from 'react';
import { Html5QrcodeScanner, QrcodeSuccessCallback, QrcodeErrorCallback, Html5QrcodeResult } from 'html5-qrcode'; // Corrected type names
import { createClient } from '@/supabase/client'; // Import Supabase client

const QrScanner = ({ userId }: { userId: string }) => {
  const scannerRef = useRef<Html5QrcodeScanner | null>(null);
  const [scannedUrl, setScannedUrl] = useState<string | null>(null);
  const [scanError, setScanError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [saveSuccess, setSaveSuccess] = useState<boolean | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const supabase = createClient();

  useEffect(() => {
    // Prevent multiple initializations
    if (scannerRef.current) {
      return;
    }

    const qrCodeScanner = new Html5QrcodeScanner(
      'qr-reader', // ID of the container element
      {
        fps: 10, // Frames per second for scanning
        qrbox: { width: 250, height: 250 }, // Size of the scanning box
        rememberLastUsedCamera: true,
        supportedScanTypes: [], // Use default scan types
      },
      false // verbose output
    );

    const onScanSuccess: QrcodeSuccessCallback = async (decodedText: string, decodedResult: Html5QrcodeResult) => { // Added types
      console.log(`Scan result: ${decodedText}`, decodedResult);
      setScannedUrl(decodedText);
      setScanError(null); // Clear previous scan errors
      setSaveSuccess(null); // Reset save status
      setSaveError(null); // Reset save error
      setIsSaving(true);

      // Stop scanning after success
      if (scannerRef.current) {
        try {
          await scannerRef.current.clear();
          scannerRef.current = null; // Ensure it can be re-initialized if needed
          console.log('QR Scanner stopped.');
        } catch (error) {
          console.error('Failed to clear scanner:', error);
        }
      }

      // --- Save URL to Supabase ---
      try {
        const { error } = await supabase
          .from('scanned_urls') // Ensure this table exists in your Supabase project
          .insert([{ url: decodedText, user_id: userId }]); // Ensure user_id column exists and is linked if needed

        if (error) {
          console.error('Error saving URL to Supabase:', error);
          throw new Error(error.message);
        }

        console.log('URL saved successfully:', decodedText);
        setSaveSuccess(true);
      } catch (err: unknown) { // Use unknown instead of any
        console.error('Failed to save URL:', err);
        let errorMessage = 'An unknown error occurred while saving.';
        if (err instanceof Error) {
          errorMessage = err.message; // Safely access message property
        }
        setSaveError(errorMessage);
        setSaveSuccess(false);
      } finally {
        setIsSaving(false);
      }
    };

    const onScanFailure: QrcodeErrorCallback = (error: any) => { // Use 'any' or 'unknown' and check type
      let errorMessage = 'Unknown scan error';
      let errorToLog: any = error; // Keep original error for logging if needed

      if (typeof error === 'string') {
        errorMessage = error;
        // Ignore common non-errors passed as strings
        if (error.includes('NotFoundException') || error.includes('No MultiFormat Readers found')) {
          // console.warn(`Code scan error (string, ignored): ${error}`);
          return;
        }
      } else if (error instanceof Error) {
        errorMessage = error.message;
        // Potentially ignore specific error messages if needed
        // if (errorMessage.includes('some specific message')) return;
      } else if (typeof error === 'object' && error !== null && 'message' in error) {
        // Handle cases where it might be an error-like object
        errorMessage = String(error.message);
      } else {
        // Fallback for other types
        try {
          errorMessage = JSON.stringify(error);
        } catch { /* Ignore stringify errors */ }
      }

      console.error(`Code scan error:`, errorToLog); // Log the original error object/value
      setScanError(errorMessage); // Set the extracted or stringified message
    };

    qrCodeScanner.render(onScanSuccess, onScanFailure);
    scannerRef.current = qrCodeScanner;

    // Cleanup function to stop the scanner when the component unmounts
    return () => {
      if (scannerRef.current) {
        scannerRef.current.clear().catch(err => {
          console.error('Failed to clear html5QrcodeScanner on unmount:', err);
        });
        scannerRef.current = null;
        console.log('QR Scanner cleared on unmount.');
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]); // Re-run effect if userId changes (though unlikely needed here)

  return (
    <div className="my-4 p-4 border rounded-lg shadow">
      <h2 className="text-xl font-semibold mb-2">Scan QR Code</h2>
      <div id="qr-reader" style={{ width: '100%', maxWidth: '500px', margin: '0 auto' }}></div>
      {scanError && <p className="text-red-500 mt-2">Scan Error: {scanError}</p>}
      {isSaving && <p className="text-blue-500 mt-2">Saving URL...</p>}
      {saveSuccess === true && <p className="text-green-500 mt-2">URL saved successfully: {scannedUrl}</p>}
      {saveSuccess === false && <p className="text-red-500 mt-2">Failed to save URL: {saveError}</p>}
      {scannedUrl && !isSaving && saveSuccess === null && (
         <p className="text-gray-600 mt-2">Scanned: {scannedUrl}</p>
      )}
       {/* Add a button to restart scanning if needed */}
       {scannedUrl && !scannerRef.current && (
         <button
           onClick={() => window.location.reload()} // Simple way to re-initialize for now
           className="mt-4 px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
         >
           Scan Another QR Code
         </button>
       )}
    </div>
  );
};

export default QrScanner;
