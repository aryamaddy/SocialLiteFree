package com.socialitefree.app.webview

import android.view.View
import android.webkit.WebChromeClient
import android.widget.FrameLayout

class SocialWebChromeClient(
    private val onProgressChangedCallback: ((Int) -> Unit)? = null,
    private val onCustomViewToggled: ((View?, WebChromeClient.CustomViewCallback?) -> Unit)? = null
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback?.invoke(newProgress)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        super.onShowCustomView(view, callback)
        customView = view
        customViewCallback = callback
        onCustomViewToggled?.invoke(view, callback)
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        onCustomViewToggled?.invoke(null, null)
    }
}
