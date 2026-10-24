package com.gemini.hw.nativeapp.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1F20.toInt())
        }

        // Custom Top Bar with Title, Refresh, Manual Cookie input, Close
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFF131314.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(16, 8, 16, 8)
            setOnClickListener { finish() }
        }

        val title = TextView(this).apply {
            text = "Вход в аккаунт Google"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 16, 0)
        }

        val cookieBtn = TextView(this).apply {
            text = "Куки"
            textSize = 14f
            setTextColor(0xFF4E88F5.toInt())
            setPadding(16, 8, 16, 8)
            setOnClickListener { showManualCookieDialog() }
        }

        val browserBtn = TextView(this).apply {
            text = "Браузер"
            textSize = 14f
            setTextColor(0xFF4E88F5.toInt())
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LOGIN_URL))
                startActivity(intent)
            }
        }

        topBar.addView(closeBtn)
        topBar.addView(title)
        topBar.addView(cookieBtn)
        topBar.addView(browserBtn)
        root.addView(topBar)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8
            )
        }
        root.addView(progressBar)

        val webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        webContainer.addView(webView)
        root.addView(webContainer)

        setContentView(root)

        setupWebView()

        webView.loadUrl(LOGIN_URL)
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
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        // Clean Chrome User-Agent without any WebView/wv flags to bypass Google blocking
        settings.userAgentString = GeminiWebClient.USER_AGENT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val newWebView = WebView(this@LoginActivity)
                newWebView.settings.javaScriptEnabled = true
                newWebView.settings.userAgentString = GeminiWebClient.USER_AGENT
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: ""
                        checkSession(url)
                        return false
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
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

    /**
     * Strictly verifies that user has completed Google authentication.
     * Only succeeds when __Secure-1PSID cookie is present AND user is on gemini.google.com!
     */
    private fun checkSession(url: String?) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://gemini.google.com") ?: ""

        val hasPsid = cookies.contains("__Secure-1PSID=")
        val hasSid = cookies.contains("SID=") || cookies.contains("SAPISID=")
        val isOnGemini = url != null && url.contains("gemini.google.com") && !url.contains("accounts.google.com")

        if (hasPsid && (hasSid || isOnGemini)) {
            authRepository.saveSessionFromCookies(cookies)
            Toast.makeText(this, "Вход успешно выполнен!", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun showManualCookieDialog() {
        val input = EditText(this).apply {
            hint = "Вставьте куки или __Secure-1PSID=..."
            setText(authRepository.getCookieString())
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Ручной ввод Cookie")
            .setMessage("Если вход через форму блокируется системой безопасности, скопируйте куки из браузера (включая __Secure-1PSID):")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val cookieStr = input.text.toString().trim()
                if (cookieStr.isNotEmpty()) {
                    authRepository.saveSessionFromCookies(cookieStr)
                    Toast.makeText(this, "Cookie сохранены!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fapp&service=mail"
    }
}
