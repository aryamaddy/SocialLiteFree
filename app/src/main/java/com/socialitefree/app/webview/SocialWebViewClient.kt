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

        // Block intent:// and native deep-link schemes that try to bounce out of SocialLite
        if (scheme != "http" && scheme != "https") {
            Log.d(TAG, "Blocked external scheme: $scheme ($url)")
            return true
        }

        // Check user's Unlimited Website & Keyword Blocker inside built-in browser too
        val rawUrl = url.toString()
        val settings = getSettings()
        if (settings.blockerPlan != com.socialitefree.app.data.BlockerPlan.PAUSED) {
            val matchedRule = settings.blockedSites.firstOrNull { it.matchesUrl(rawUrl) }
            if (matchedRule != null) {
                view?.loadDataWithBaseURL(
                    null,
                    """<html><body style="background:#0F172A;color:#F8FAFC;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center;padding:24px;">
                        <div><h2>Blocked by SocialLite</h2><p style="color:#94A3B8">Rule matched: <b>${matchedRule.pattern}</b></p><button onclick="history.back()" style="margin-top:16px;padding:10px 20px;border-radius:12px;border:none;background:#2563EB;color:white;font-weight:bold;">Go Back</button></div></body></html>""",
                    "text/html",
                    "utf-8",
                    null
                )
                return true
            }
        }

        // Redirect blocked Reels/Shorts URLs when enabled
        val host = url.host?.lowercase() ?: ""
        val path = url.path?.lowercase() ?: ""

        if (platform == Platform.INSTAGRAM && settings.igBlockReels && host.contains("instagram.com")) {
            if (path == "/reels" || path.startsWith("/reels/") || path.startsWith("/reel/")) {
                view?.loadUrl("https://www.instagram.com/")
                return true
            }
        }

        if (platform == Platform.YOUTUBE && settings.ytHideShorts && host.contains("youtube.com")) {
            if (path.startsWith("/shorts/")) {
                view?.loadUrl("https://m.youtube.com/")
                return true
            }
        }

        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // Early injection for scripts that hook JSON.parse / fetch (YouTube & Instagram)
        if (platform == Platform.INSTAGRAM || platform == Platform.YOUTUBE) {
            injectPlatformScript(view)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        reinjectScripts(view)
        onPageFinishedCallback?.invoke(url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // SPA navigation support (YouTube, Instagram, X, Reddit)
        reinjectScripts(view)
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
        super.onReceivedSslError(view, handler, error)
    }

    fun reinjectScripts(view: WebView?) {
        if (view == null) return
        val settings = getSettings()

        try {
            // 1. Common controls (grayscale + open-app banner dismissal)
            val commonJs = readAsset("js/common_controls.js")
            if (commonJs.isNotEmpty()) {
                val injectedCommon = commonJs.replace("/*CONFIG*/{}", settings.toCommonConfigJson())
                view.evaluateJavascript(injectedCommon, null)
            }

            // 2. Platform-specific controls
            injectPlatformScript(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting scripts", e)
        }
    }

    private fun injectPlatformScript(view: WebView?) {
        if (view == null) return
        val settings = getSettings()
        val configJson = settings.toPlatformConfigJson(platform)

        try {
            val platformJs = readAsset(platform.jsScriptAsset)
            if (platformJs.isNotEmpty() && platform.jsScriptAsset != "js/common_controls.js") {
                val injected = when (platform) {
                    Platform.YOUTUBE -> {
                        platformJs.replace("__CONFIG__", configJson)
                    }
                    Platform.INSTAGRAM -> {
                        platformJs
                            .replace("/*CONFIG*/null", configJson)
                            .replace("__NAMESPACE__", "SocialLite_")
                    }
                    Platform.FACEBOOK, Platform.REDDIT, Platform.SNAPCHAT -> {
                        platformJs
                            .replace("/*CONFIG*/null", configJson)
                            .replace("__NAMESPACE__", "SocialLite_")
                    }
                    else -> {
                        platformJs.replace("/*CONFIG*/{}", configJson)
                    }
                }
                view.evaluateJavascript(injected, null)
            }

            // Extra Instagram UI controls (Reels tab hiding, Comments hiding, Create button hiding)
            if (platform == Platform.INSTAGRAM) {
                view.evaluateJavascript(buildInstagramUiControlsJs(settings), null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting platform script for ${platform.id}", e)
        }
    }

    private fun buildInstagramUiControlsJs(settings: AppSettings): String {
        val cssRules = StringBuilder()
        // Always hide native "Open App" / "Use the app" banners on Instagram mobile web
        cssRules.append("[class*=\"InstallApp\" i], a[href*=\"applink\"], div[role=\"dialog\"]:has(a[href*=\"instagram.com/download\"]) { display: none !important; } ")
        if (settings.igBlockReels) {
            cssRules.append("a[href^=\"/reels\"], a[href*=\"/reels/\"], svg[aria-label=\"Reels\"] { display: none !important; } ")
            cssRules.append("a[href^=\"/reels\"]:has(svg) { pointer-events: none !important; } ")
        }
        if (settings.igHideComments) {
            cssRules.append("textarea[aria-label*=\"comment\" i], form:has(textarea[aria-label*=\"comment\" i]), a[href*=\"/comments/\"] { display: none !important; } ")
        }
        if (settings.igBlockPosting) {
            cssRules.append("a[href^=\"/create\"], [aria-label=\"New post\"], [aria-label=\"Create\"], svg[aria-label=\"New post\"] { display: none !important; pointer-events: none !important; } ")
        }

        val escapedCss = cssRules.toString().replace("'", "\\'")
        val blockReelsFlag = settings.igBlockReels

        return """
            (function() {
                if (!/instagram\.com$/i.test(location.hostname)) return;
                var st = document.getElementById('__sl_ig_ui_css') || document.createElement('style');
                st.id = '__sl_ig_ui_css';
                st.textContent = '$escapedCss';
                (document.head || document.documentElement).appendChild(st);
                if ($blockReelsFlag && /^\/reels?(\/|$)/.test(location.pathname)) {
                    location.replace('https://www.instagram.com/');
                }
            })();
        """.trimIndent()
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
