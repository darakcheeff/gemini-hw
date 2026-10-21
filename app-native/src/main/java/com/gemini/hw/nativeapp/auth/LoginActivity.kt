package com.gemini.hw.nativeapp.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.network.GeminiWebClient

class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var authRepository: AuthRepository

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authRepository = AuthRepository.getInstance(this)

        val layout = android.widget.FrameLayout(this)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                8
            )
        }

        webView = WebView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        layout.addView(webView)
        layout.addView(progressBar)
        setContentView(layout)

        setupWebView()

        webView.loadUrl(GeminiWebClient.GEMINI_APP_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Crucial for Huawei devices without GMS: Standard mobile Chrome UA
        settings.userAgentString = GeminiWebClient.USER_AGENT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                checkSession(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkSession(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                checkSession(url)
                return false
            }
        }
    }

    private fun checkSession(url: String?) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://gemini.google.com") ?: ""

        if (cookies.contains("__Secure-1PSID") || (url != null && url.contains("gemini.google.com/app"))) {
            if (cookies.isNotEmpty()) {
                authRepository.saveSessionFromCookies(cookies)
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
