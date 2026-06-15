package com.richwatson.electrofind.ui.screens

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.richwatson.electrofind.viewmodel.AuthViewModel

private const val TAG = "LoginScreen"

@Composable
fun LoginScreen(authViewModel: AuthViewModel) {
    // When non-null, this WebView is shown as a full-screen overlay for OAuth popups
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    fun pollForToken(attempts: Int = 0) {
        if (attempts > 20) return
        val cookies = CookieManager.getInstance().getCookie("https://electroverse.com") ?: ""
        if (cookies.contains("token=")) {
            Log.d(TAG, "Token cookie found on attempt $attempts")
            authViewModel.extractAndSaveWebViewCookies()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ pollForToken(attempts + 1) }, 500)
        }
    }

    fun makePopupWebView(ctx: android.content.Context, parentView: WebView): WebView {
        return WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.userAgentString = parentView.settings.userAgentString
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "popup onPageFinished: $url")
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    Log.d(TAG, "popup shouldOverride: ${request.url}")
                    return false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                    Log.d(TAG, "popup onCreateWindow (nested)")
                    // Nested popups: create another layer
                    val nested = makePopupWebView(ctx, view)
                    popupWebView = nested
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = nested
                    resultMsg.sendToTarget()
                    return true
                }
                override fun onCloseWindow(window: WebView) {
                    Log.d(TAG, "popup closed — polling for token")
                    popupWebView = null
                    pollForToken()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            Log.d(TAG, "onPageFinished: $url")
                            view.postDelayed({ view.evaluateJavascript("window.scrollTo(0,0);", null) }, 300)
                        }
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            Log.d(TAG, "doUpdateVisitedHistory: $url")
                            if (url.contains("electroverse.com") && !url.contains("log-in") && !url.contains("google")) {
                                Log.d(TAG, "Login URL detected: $url")
                                authViewModel.extractAndSaveWebViewCookies()
                            }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            Log.d(TAG, "shouldOverride: ${request.url}")
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                            Log.d(TAG, "onCreateWindow")
                            val popup = makePopupWebView(ctx, view)
                            popupWebView = popup
                            val transport = resultMsg.obj as WebView.WebViewTransport
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                        override fun onCloseWindow(window: WebView) {
                            Log.d(TAG, "main onCloseWindow — polling for token")
                            popupWebView = null
                            pollForToken()
                        }
                    }

                    loadUrl("https://electroverse.com/log-in")
                }
            }
        )

        // Show OAuth popup WebView as a full-screen overlay when active
        popupWebView?.let { popup ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { popup }
            )
        }
    }
}
