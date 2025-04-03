'use client';

import { useState, useEffect } from 'react';
import { createClient } from '@/supabase/client'; // Use client-side Supabase client

// Define TypeScript types based on the API response and DB schema
interface ReceiptItem {
  id: string;
  name: string;
  quantity: number;
  price: number;
}

interface Receipt {
  id: string;
  receipt_date: string | null;
  total_amount: number | null;
  store_name: string | null;
  uid: string | null;
  created_at: string;
  receipt_items: ReceiptItem[];
}

export default function ReceiptsPage() {
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedReceipt, setSelectedReceipt] = useState<Receipt | null>(null);
  const supabase = createClient(); // Initialize client-side client

  // Function to fetch receipts
  const fetchReceipts = async () => {
    setLoading(true);
    setError(null);

    // Check if user is logged in before fetching
    const { data: { session } } = await supabase.auth.getSession();
    if (!session) {
      setError('You must be logged in to view receipts.');
      setLoading(false);
      // Optionally redirect to login or show login prompt
      return;
    }

    try {
      // Use fetch API to call our backend endpoint
      const response = await fetch('/api/receipts');
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
      }
      const data: Receipt[] = await response.json();
      setReceipts(data);
    } catch (e: unknown) { // Change type to unknown
      console.error("Failed to fetch receipts:", e);
      // Type check before accessing message
      const errorMessage = e instanceof Error ? e.message : 'Failed to load receipts.';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // Initial fetch on component mount
  useEffect(() => {
    fetchReceipts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Run only once on mount

  // Subscribe to realtime updates
  useEffect(() => {
    // Ensure user is logged in before subscribing
    const checkAuthAndSubscribe = async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        console.log("User not logged in, skipping realtime subscription.");
        return null; // Don't subscribe if not logged in
      }

      console.log(`Setting up realtime subscription for user: ${session.user.id}`);
      const channel = supabase
        .channel('receipts_changes')
        .on(
          'postgres_changes',
          {
            event: 'INSERT',
            schema: 'public',
            table: 'receipts',
            filter: `user_id=eq.${session.user.id}` // Only listen for changes for the current user
          },
          (payload) => {
            console.log('Realtime: New receipt detected!', payload);
            // Re-fetch receipts when a new one is inserted for this user
            fetchReceipts();
          }
        )
        .subscribe((status, err) => {
          if (status === 'SUBSCRIBED') {
            console.log('Realtime: Subscribed to receipts changes');
          } else if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT') {
            console.error(`Realtime: Subscription error - ${status}`, err);
            setError(`Realtime subscription failed: ${status}`);
          } else if (status === 'CLOSED') {
            console.log('Realtime: Subscription closed');
          }
        });

      return channel;
    };

    let subscriptionChannel: ReturnType<typeof supabase.channel> | null = null;
    checkAuthAndSubscribe().then(channel => {
      subscriptionChannel = channel;
    });

    // Cleanup function to remove the subscription when the component unmounts
    return () => {
      if (subscriptionChannel) {
        console.log('Realtime: Unsubscribing from receipts changes');
        supabase.removeChannel(subscriptionChannel).catch(err => {
          console.error("Realtime: Error removing channel", err);
        });
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supabase]); // Re-run if supabase client instance changes

  const formatDate = (dateString: string | null) => {
    if (!dateString) return 'N/A';
    try {
      return new Date(dateString).toLocaleDateString('el-GR', { // Greek locale for date format
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateString; // Fallback if date is invalid
    }
  };

  const formatCurrency = (amount: number | null) => {
    if (amount === null || amount === undefined) return 'N/A';
    return amount.toLocaleString('el-GR', { style: 'currency', currency: 'EUR' });
  };

  if (loading) {
    return <div className="flex justify-center items-center h-screen"><p className="text-lg text-gray-500">Loading receipts...</p></div>;
  }

  if (error) {
    return <div className="flex justify-center items-center h-screen"><p className="text-lg text-red-500">Error: {error}</p></div>;
  }

  return (
    <div className="container mx-auto p-4 md:p-8">
      <h1 className="text-3xl font-bold mb-6 text-gray-800">My Receipts</h1>

      {receipts.length === 0 ? (
        <p className="text-gray-600">You have no receipts yet.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Receipts List */}
          <div className="md:col-span-1 bg-white shadow-md rounded-lg p-4 overflow-y-auto max-h-[80vh]">
            <h2 className="text-xl font-semibold mb-4 text-gray-700">Receipts ({receipts.length})</h2>
            <ul className="space-y-3">
              {receipts.map((receipt) => (
                <li
                  key={receipt.id}
                  onClick={() => setSelectedReceipt(receipt)}
                  className={`p-3 rounded-md cursor-pointer transition-colors duration-150 ease-in-out ${selectedReceipt?.id === receipt.id ? 'bg-blue-100 border border-blue-300' : 'hover:bg-gray-100 border border-transparent'}`}
                >
                  <div className="flex justify-between items-center">
                    <span className="font-medium text-gray-800 truncate">{receipt.store_name || 'Unknown Store'}</span>
                    <span className={`text-sm font-semibold ${selectedReceipt?.id === receipt.id ? 'text-blue-700' : 'text-gray-600'}`}>
                      {formatCurrency(receipt.total_amount)}
                    </span>
                  </div>
                  <p className={`text-xs ${selectedReceipt?.id === receipt.id ? 'text-blue-600' : 'text-gray-500'}`}>
                    {formatDate(receipt.receipt_date)}
                  </p>
                </li>
              ))}
            </ul>
          </div>

          {/* Receipt Details */}
          <div className="md:col-span-2 bg-white shadow-md rounded-lg p-6">
            {selectedReceipt ? (
              <div>
                <h2 className="text-2xl font-semibold mb-4 text-gray-800">{selectedReceipt.store_name || 'Receipt Details'}</h2>
                <div className="mb-4 space-y-1 text-sm text-gray-600">
                  <p><strong>Date:</strong> {formatDate(selectedReceipt.receipt_date)}</p>
                  <p><strong>Total:</strong> <span className="font-bold text-lg text-green-700">{formatCurrency(selectedReceipt.total_amount)}</span></p>
                  {selectedReceipt.uid && <p><strong>UID:</strong> {selectedReceipt.uid}</p>}
                  <p><strong>Scanned:</strong> {formatDate(selectedReceipt.created_at)}</p>
                </div>

                <h3 className="text-xl font-semibold mb-3 mt-6 text-gray-700 border-t pt-4">Items ({selectedReceipt.receipt_items.length})</h3>
                {selectedReceipt.receipt_items.length > 0 ? (
                  <ul className="space-y-2 max-h-[50vh] overflow-y-auto pr-2">
                    {selectedReceipt.receipt_items.map((item) => (
                      <li key={item.id} className="flex justify-between items-center p-2 bg-gray-50 rounded">
                        <div className="flex-1 mr-2">
                          <span className="text-sm text-gray-800">{item.name}</span>
                          {item.quantity > 1 && <span className="text-xs text-gray-500 ml-2"> (Qty: {item.quantity})</span>}
                        </div>
                        <span className="text-sm font-medium text-gray-700">{formatCurrency(item.price)}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-gray-500">No items found for this receipt.</p>
                )}
              </div>
            ) : (
              <div className="flex justify-center items-center h-full">
                <p className="text-gray-500">Select a receipt from the list to view details.</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
