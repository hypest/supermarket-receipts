import React, { useState, useEffect, useCallback } from 'react';
import { StyleSheet, View, Text, Alert, ActivityIndicator, Button, Linking, AppState, Platform } from 'react-native';
import QRCodeScanner from 'react-native-qrcode-scanner';
import { RNCamera } from 'react-native-camera'; // Still needed for types/constants potentially
import { supabase } from '../lib/supabaseClient';
import { check, request, PERMISSIONS, RESULTS, openSettings } from 'react-native-permissions';

interface QrScannerProps {
    userId: string;
}

// Define possible permission statuses
type PermissionStatus = 'unavailable' | 'denied' | 'limited' | 'granted' | 'blocked' | 'checking';

const QrScannerComponent = ({ userId }: QrScannerProps) => {
    const [isSaving, setIsSaving] = useState(false);
    const [lastScannedData, setLastScannedData] = useState<string | null>(null);
    const [scannerActive, setScannerActive] = useState(true); // Control if scanner should read codes
    const [cameraPermission, setCameraPermission] = useState<PermissionStatus>('checking');
    const scannerRef = React.useRef<QRCodeScanner>(null); // Ref for the scanner

    // Function to check and request camera permission
    const checkAndRequestPermission = useCallback(async () => {
        const permission = Platform.OS === 'ios' ? PERMISSIONS.IOS.CAMERA : PERMISSIONS.ANDROID.CAMERA;
        const status = await check(permission);
        setCameraPermission(status);

        if (status === RESULTS.DENIED) {
            const requestedStatus = await request(permission);
            setCameraPermission(requestedStatus);
        }
    }, []);

    useEffect(() => {
        checkAndRequestPermission();

        // Re-check permission when app comes back to foreground
        const subscription = AppState.addEventListener('change', (nextAppState) => {
            if (nextAppState === 'active') {
                checkAndRequestPermission();
            }
        });

        return () => {
            subscription.remove();
        };
    }, [checkAndRequestPermission]);

    // Make onSuccess synchronous to match expected prop type
    const onSuccess = (e: { data: string }) => {
        if (isSaving || !scannerActive) return; // Don't process if saving or inactive

        const scannedUrl = e.data;
        console.log('QR Code Detected:', scannedUrl);

        // Pause scanning and show saving indicator
        setScannerActive(false); // Deactivate scanner reading
        setIsSaving(true);
        setLastScannedData(scannedUrl);

        // --- Save URL to Supabase (using async IIAFE) ---
        (async () => {
            try {
                const { error } = await supabase
                    .from('scanned_urls')
                    .insert([{ url: scannedUrl, user_id: userId }]);

                if (error) throw error; // Throw to be caught by catch block

                console.log('URL saved successfully:', scannedUrl);
                Alert.alert('Success', `URL saved: ${scannedUrl}`, [
                    { text: 'OK' }, // Keep scanner paused
                ]);

            } catch (err: unknown) {
                console.error('Failed to save URL:', err);
                let errorMessage = 'An unknown error occurred.';
                 if (err instanceof Error) {
                    errorMessage = err.message;
                 } else if (typeof err === 'object' && err !== null && 'message' in err) {
                    // Handle Supabase specific error structure if needed
                    errorMessage = String(err.message);
                 }
                Alert.alert('Error', `Failed to save URL: ${errorMessage}`, [
                    { text: 'Try Again', onPress: reactivateScanner }, // Allow retry
                ]);
            } finally {
                setIsSaving(false);
            }
        })(); // Immediately invoke the async function
    };

    // Function to reactivate the scanner
    const reactivateScanner = () => {
        setLastScannedData(null);
        setScannerActive(true);
        // Use reactivate method if available and needed, otherwise just setting state might be enough
        scannerRef.current?.reactivate();
    };

    // Render different UI based on permission status
    if (cameraPermission === 'checking') {
        return (
            <View style={styles.centerMessage}>
                <ActivityIndicator size="large" />
                <Text>Checking camera permission...</Text>
            </View>
        );
    }

    if (cameraPermission === 'denied' || cameraPermission === 'blocked') {
        return (
            <View style={styles.centerMessage}>
                <Text style={styles.permissionText}>Camera permission is required to scan QR codes.</Text>
                {cameraPermission === 'blocked' ? (
                    <Button title="Open Settings" onPress={() => openSettings()} />
                ) : (
                    <Button title="Grant Permission" onPress={checkAndRequestPermission} />
                )}
            </View>
        );
    }

    if (cameraPermission !== 'granted') {
         return (
            <View style={styles.centerMessage}>
                <Text style={styles.permissionText}>Camera permission is {cameraPermission}.</Text>
                 <Button title="Check Again" onPress={checkAndRequestPermission} />
            </View>
        );
    }

    // Render Scanner only if permission is granted
    return (
        <View style={styles.container}>
            <QRCodeScanner
                ref={scannerRef}
                // Pass a no-op function when inactive to satisfy types
                onRead={scannerActive ? onSuccess : () => {}}
                flashMode={RNCamera.Constants.FlashMode.off}
                reactivate={false} // We manually reactivate
                // reactivateTimeout={5000} // Optional: auto-reactivate after timeout
                cameraStyle={styles.cameraContainer}
                containerStyle={styles.scannerContainerFull}
                topContent={
                    <View style={styles.statusOverlay}>
                        {isSaving && (
                            <View style={styles.savingIndicator}>
                                <ActivityIndicator size="small" color="#ffffff" />
                                <Text style={styles.savingText}>Saving...</Text>
                            </View>
                        )}
                        {!scannerActive && !isSaving && lastScannedData && (
                            <View style={styles.scanPausedOverlay}>
                                <Text style={styles.scanPausedText}>Scan Complete</Text>
                                <Text style={styles.scanPausedData} numberOfLines={1}>{lastScannedData}</Text>
                                <Button title="Scan Another" onPress={reactivateScanner} />
                            </View>
                        )}
                    </View>
                }
                // bottomContent can be added here if needed
            />
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    centerMessage: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        padding: 20,
    },
    permissionText: {
        textAlign: 'center',
        marginBottom: 15,
        fontSize: 16,
    },
    scannerContainerFull: {
        flex: 1,
        backgroundColor: 'black', // Ensure background is black
    },
    cameraContainer: {
        height: '100%', // Make camera fill the container
    },
    statusOverlay: {
        position: 'absolute',
        top: 20, // Position overlay at the top
        left: 0,
        right: 0,
        alignItems: 'center',
        zIndex: 10, // Ensure it's above the camera view
    },
    savingIndicator: {
        flexDirection: 'row',
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        paddingVertical: 8,
        paddingHorizontal: 15,
        borderRadius: 20,
        alignItems: 'center',
    },
    savingText: {
        color: '#ffffff',
        marginLeft: 10,
    },
    scanPausedOverlay: {
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        padding: 15,
        borderRadius: 10,
        alignItems: 'center',
        width: '90%',
    },
    scanPausedText: {
        fontSize: 16,
        fontWeight: 'bold',
        color: '#ffffff',
        marginBottom: 5,
    },
    scanPausedData: {
        fontSize: 12,
        color: '#ffffff',
        marginBottom: 10,
        textAlign: 'center',
    },
});

export default QrScannerComponent;
