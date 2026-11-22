package com.socialitefree.app.webview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
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
            Log.d(TAG, "Blocked non-web navigation scheme: $scheme")
            return true
        }

        val host = url.host?.lowercase() ?: ""
        // Allow navigation within platform domain or common auth domains
        if (host.contains(platform.host) || host.contains("accounts.google.com") || host.contains("login") || host.contains("auth")) {
            return false
        }

        // Keep inside WebView or handle external
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectScripts(view)
        onPageFinishedCallback?.invoke(url)
    }

    private fun injectScripts(view: WebView?) {
        if (view == null) return
        val settings = getSettings()

        // 1. Inject common controls first (grayscale, open-app prompt blocking)
        val commonJs = readAsset("js/common_controls.js")
        if (commonJs.isNotEmpty()) {
            val injectedCommon = commonJs.replace("/*CONFIG*/{}", settings.toCommonConfigJson())
            view.evaluateJavascript(injectedCommon, null)
        }

        // 2. Inject platform-specific controls
        val platformJs = readAsset(platform.jsScriptAsset)
        if (platformJs.isNotEmpty() && platform.jsScriptAsset != "js/common_controls.js") {
            val injectedPlatform = platformJs.replace("/*CONFIG*/{}", settings.toPlatformConfigJson(platform))
            view.evaluateJavascript(injectedPlatform, null)
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
