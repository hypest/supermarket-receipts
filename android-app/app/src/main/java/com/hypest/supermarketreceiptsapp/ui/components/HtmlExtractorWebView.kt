package com.hypest.supermarketreceiptsapp.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val TAG = "HtmlExtractorWebView"
private const val JS_INTERFACE_NAME = "AndroidHtmlExtractor"
// Timeout in milliseconds to wait after onPageFinished before attempting extraction
private const val EXTRACTION_DELAY_MS = 5000L // Adjust as needed

/**
 * A composable that loads a URL in a hidden WebView, waits for potential
 * client-side rendering, extracts the final HTML, and calls a callback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlExtractorWebView(
    url: String,
    modifier: Modifier = Modifier, // Keep modifier for potential layout needs (e.g., size 0)
    onHtmlExtracted: (url: String, html: String) -> Unit,
    onError: (url: String, error: String) -> Unit
) {
    // Remember the WebView state
    val webView = remember { mutableMapOf<String, WebView?>() }

    // JavaScript Interface class
    class JavaScriptInterface(
        private val onHtmlReceived: (String) -> Unit,
        private val onErrorOccurred: (String) -> Unit
    ) {
        @JavascriptInterface
        fun processHTML(html: String?) {
            if (html != null) {
                Log.d(TAG, "Received HTML (length: ${html.length}) via JS Interface")
                onHtmlReceived(html)
            } else {
                Log.w(TAG, "Received null HTML via JS Interface")
                onErrorOccurred("JavaScript extraction returned null HTML.")
            }
        }

        @JavascriptInterface
        fun logError(error: String) {
            Log.e(TAG, "JavaScript Error: $error")
            onErrorOccurred("JavaScript Error: $error")
        }
    }

    // Use AndroidView to embed the WebView
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true // May be needed by some sites

                // Create and add the JavaScript interface
                val jsInterface = JavaScriptInterface(
                    onHtmlReceived = { html -> onHtmlExtracted(url, html) },
                    onErrorOccurred = { error -> onError(url, error) }
                )
                addJavascriptInterface(jsInterface, JS_INTERFACE_NAME)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Log.d(TAG, "Page loading started: $url")
                    }

                    override fun onPageFinished(view: WebView?, currentUrl: String?) {
                        super.onPageFinished(view, currentUrl)
                        Log.d(TAG, "Page finished loading: $currentUrl. Waiting ${EXTRACTION_DELAY_MS}ms before extraction...")

                        // Inject JavaScript to extract HTML after a delay
                        // This delay gives client-side frameworks (like Blazor) time to render
                        view?.postDelayed({
                            Log.d(TAG, "Attempting to inject JS for HTML extraction...")
                            val jsCode = """
                                (function() {
                                    try {
                                        var htmlContent = document.documentElement.outerHTML;
                                        if (htmlContent) {
                                            $JS_INTERFACE_NAME.processHTML(htmlContent);
                                        } else {
                                            $JS_INTERFACE_NAME.logError('document.documentElement.outerHTML was null or empty');
                                        }
                                    } catch (e) {
                                        $JS_INTERFACE_NAME.logError(e.message || String(e));
                                    }
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(jsCode, null)
                        }, EXTRACTION_DELAY_MS)
                    }

                     override fun onReceivedError(
                         view: WebView?,
                         errorCode: Int,
                         description: String?,
                         failingUrl: String?
                     ) {
                         super.onReceivedError(view, errorCode, description, failingUrl)
                         Log.e(TAG, "WebView error: Code $errorCode, Desc: $description, URL: $failingUrl")
                         onError(url, "WebView error code $errorCode: $description")
                     }
                }
                // Store the WebView instance to potentially clean up later if needed
                webView[url] = this
            }
        },
        update = { view ->
            // Load the URL when the composable is updated with a new URL (or initially)
            // Check if the URL is already loaded to prevent reloading on recomposition
            if (view.url != url) {
                 Log.d(TAG, "Loading URL in WebView: $url")
                 view.loadUrl(url)
            }
        },
        modifier = modifier // Apply modifier (e.g., size(0.dp) to hide it)
    )

    // Optional: Clean up WebView when the composable leaves composition
    // DisposableEffect might be complex if the WebView needs to persist across recompositions
    // Consider managing WebView lifecycle more explicitly if needed.
}
