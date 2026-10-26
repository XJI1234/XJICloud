package com.xjicloud.xjicloud_mobile

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.view.WindowInsets
import com.mobile.auth.gatewayauth.Constant

/** Displays the actual policy URL supplied by the operator SDK; never fabricates a policy. */
class FusionProtocolActivity : Activity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(Constant.PROTOCOL_WEB_VIEW_NAME).orEmpty()
        val url = intent.getStringExtra(Constant.PROTOCOL_WEB_VIEW_URL).orEmpty()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.addView(Button(this).apply { text = getString(R.string.fusion_policy_back); setOnClickListener { finish() } })
        root.addView(TextView(this).apply { text = title })
        setContentView(root)
        if (!isSecurePolicy(url)) {
            root.addView(TextView(this).apply { text = getString(R.string.fusion_policy_unavailable) })
            return
        }
        val browser = WebView(this)
        web = browser
        browser.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !isSecurePolicy(request.url.toString())
        }
        root.addView(browser, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        browser.loadUrl(url)
    }

    override fun onDestroy() {
        web?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        web = null
        super.onDestroy()
    }

    private fun isSecurePolicy(value: String): Boolean = try {
        val uri = Uri.parse(value)
        uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.userInfo == null
    } catch (_: IllegalArgumentException) { false }

    companion object { const val ACTION = "com.fusion.auth" }
}
