package com.socialitefree.app.webview

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.socialitefree.app.data.AppSettings
import com.socialitefree.app.data.Platform
import java.io.BufferedReader
import java.io.InputStreamReader

class SocialWebViewClient(
    private val context: Context,
    private val platform: Platform,
    private val getSettings: () -> AppSettings,
    private val onPageFinishedCallback: ((String?) -> Unit)? = null
) : WebViewClient() {

    companion object {
        private const val TAG = "SocialWebViewClient"
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        val scheme = url.scheme?.lowercase()

        // Block intent:// and app-specific custom schemes that try to open installed apps
        if (scheme != "http" && scheme != "https") {
            Log.d(TAG, "Blocked non-web navigation scheme: $scheme ($url)")
            return true
        }

        // Allow all web URLs to stay inside WebView
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted: $url")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished: $url")
        injectScripts(view)
        onPageFinishedCallback?.invoke(url)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            Log.e(TAG, "Main frame error: ${error?.errorCode} - ${error?.description}")
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.e(TAG, "SSL Error: $error")
        // Proceed or super depending on standard
        super.onReceivedSslError(view, handler, error)
    }

    private fun injectScripts(view: WebView?) {
        if (view == null) return
        val settings = getSettings()

        try {
            // 1. Inject common controls first (grayscale, open-app prompt blocking)
            val commonJs = readAsset("js/common_controls.js")
            if (commonJs.isNotEmpty()) {
                val injectedCommon = commonJs.replace("/*CONFIG*/{}", settings.toCommonConfigJson())
                view.evaluateJavascript(injectedCommon, null)
            }

            // 2. Inject platform-specific controls
            val platformJs = readAsset(platform.jsScriptAsset)
            if (platformJs.isNotEmpty()) {
                val injectedPlatform = when (platform) {
                    Platform.YOUTUBE -> {
                        // YouTube personal.js uses __CONFIG__ placeholder
                        platformJs.replace("__CONFIG__", settings.toPlatformConfigJson(platform))
                    }
                    Platform.INSTAGRAM -> {
                        // Instagram feed_response_filter.js uses /*CONFIG*/null and __NAMESPACE__
                        platformJs
                            .replace("/*CONFIG*/null", settings.toPlatformConfigJson(platform))
                            .replace("__NAMESPACE__", "SocialLite_")
                    }
                    else -> {
                        // Other scripts use /*CONFIG*/{}
                        platformJs.replace("/*CONFIG*/{}", settings.toPlatformConfigJson(platform))
                    }
                }
                view.evaluateJavascript(injectedPlatform, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting scripts", e)
        }
    }

    private fun readAsset(path: String): String {
        return try {
            context.assets.open(path).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading asset script: $path", e)
            ""
        }
    }
}
