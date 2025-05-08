package com.hypest.supermarketreceiptsapp.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "HtmlExtractorWebView"
private const val JS_INTERFACE_NAME = "AndroidHtmlExtractor"
// Delay after page finished before attempting extraction
private const val EXTRACTION_DELAY_MS = 35000L
// Delay after page started before checking for persistent challenge iframe
private const val CHALLENGE_CHECK_DELAY_MS = 27000L // Shorter delay to check for
// challenge

/**
 * A composable that loads a URL in a WebView, attempts to detect Cloudflare challenges,
 * extracts the final HTML, and calls appropriate callbacks.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlExtractorWebView(
    url: String,
    modifier: Modifier = Modifier,
    onHtmlExtracted: (url: String, html: String) -> Unit,
    onError: (url: String, error: String) -> Unit,
    onChallengeInteractionRequired: () -> Unit // New callback
) {
    val webView = remember { mutableMapOf<String, WebView?>() }
    // Remember coroutine scope for launching delayed checks
    val coroutineScope = rememberCoroutineScope()
    var challengeCheckJob by remember { mutableStateOf<Job?>(null) }

    // JavaScript Interface class
    class JavaScriptInterface(
        private val onHtmlReceived: (String) -> Unit,
        private val onErrorOccurred: (String) -> Unit
    ) {
        @JavascriptInterface
        fun processHTML(html: String?) {
            // Ensure this runs on the main thread if it needs to update UI state via ViewModel
            coroutineScope.launch(Dispatchers.Main) {
                if (html != null) {
                    Log.d(TAG, "Received HTML (length: ${html.length}) via JS Interface")
                    onHtmlReceived(html)
                } else {
                    Log.w(TAG, "Received null HTML via JS Interface")
                    onErrorOccurred("JavaScript extraction returned null HTML.")
                }
            }
        }

        @JavascriptInterface
        fun logError(error: String) {
             coroutineScope.launch(Dispatchers.Main) {
                Log.e(TAG, "JavaScript Error: $error")
                onErrorOccurred("JavaScript Error: $error")
             }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                val jsInterface = JavaScriptInterface(
                    onHtmlReceived = { html -> onHtmlExtracted(url, html) },
                    onErrorOccurred = { error -> onError(url, error) }
                )
                addJavascriptInterface(jsInterface, JS_INTERFACE_NAME)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Log.d(TAG, "Page loading started: $url")
                        // Cancel any previous challenge check job
                        challengeCheckJob?.cancel()
                        // Schedule a check for the challenge iframe after a short delay
                        challengeCheckJob = coroutineScope.launch {
                            delay(CHALLENGE_CHECK_DELAY_MS)
                            Log.d(TAG, "Checking for persistent Cloudflare challenge iframe...")
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var iframe = document.querySelector('iframe[src*="challenges.cloudflare.com"]');
                                    // Also check for common challenge text elements
                                    var verifyText = document.body.innerText.includes('Verify you are human') || document.body.innerText.includes('Checking your browser');
                                    return (iframe !== null || verifyText);
                                })();
                                """.trimIndent()
                            ) { result ->
                                // Result is "true" or "false" as a string
                                if (result == "true") {
                                    Log.w(TAG, "Persistent Cloudflare challenge detected. Requiring user interaction.")
                                    // Call the callback on the main thread
                                     launch(Dispatchers.Main) {
                                         onChallengeInteractionRequired()
                                     }
                                } else {
                                    Log.d(TAG, "No persistent challenge detected after delay.")
                                }
                            }
                        }
                    }

                    override fun onPageFinished(view: WebView?, currentUrl: String?) {
                        super.onPageFinished(view, currentUrl)
                        Log.d(TAG, "Page finished loading: $currentUrl. Waiting ${EXTRACTION_DELAY_MS}ms before extraction...")
                        // Cancel the challenge check if page finishes quickly
                        challengeCheckJob?.cancel()

                        view?.postDelayed({
                            Log.d(TAG, "Attempting to inject JS for HTML extraction...")
                            val jsCode = """
                                (function() {
                                    try {
                                        // Check again for challenge just before extraction
                                        var iframe = document.querySelector('iframe[src*="challenges.cloudflare.com"]');
                                        var verifyText = document.body.innerText.includes('Verify you are human') || document.body.innerText.includes('Checking your browser');
                                        if (iframe !== null || verifyText) {
                                             $JS_INTERFACE_NAME.logError('Cloudflare challenge present during HTML extraction attempt.');
                                             return; // Don't extract if challenge is still there
                                        }

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
                         challengeCheckJob?.cancel() // Cancel check on error
                         onError(url, "WebView error code $errorCode: $description")
                     }
                }
                webView[url] = this
            }
        },
        update = { view ->
            if (view.url != url) {
                 Log.d(TAG, "Loading URL in WebView: $url")
                 view.loadUrl(url)
            }
        },
        modifier = modifier
    )
}
