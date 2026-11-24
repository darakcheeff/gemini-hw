package com.gemini.hw.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.network.GeminiWebClient
import com.gemini.hw.core.repository.GemsAndSparkRepository
import okhttp3.OkHttpClient

class GeminiApp : Application(), ImageLoaderFactory {

    lateinit var authRepository: AuthRepository
        private set

    lateinit var geminiWebClient: GeminiWebClient
        private set

    lateinit var gemsAndSparkRepository: GemsAndSparkRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        authRepository = AuthRepository.getInstance(this)
        geminiWebClient = GeminiWebClient(authRepository)
        geminiWebClient.webViewTokenRefresher = { refreshSessionViaHiddenWebView() }
        gemsAndSparkRepository = GemsAndSparkRepository()
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private suspend fun refreshSessionViaHiddenWebView(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        kotlinx.coroutines.withTimeoutOrNull(12000L) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                var webView: android.webkit.WebView? = null
                var finished = false
                fun finishWith(result: Boolean) {
                    if (finished) return
                    finished = true
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {}
                    webView = null
                    if (cont.isActive) cont.resume(result) {}
                }

                try {
                    val wv = android.webkit.WebView(this@GeminiApp)
                    webView = wv
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.databaseEnabled = true
                    wv.settings.userAgentString = GeminiWebClient.USER_AGENT
                    val cm = android.webkit.CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(wv, true)

                    wv.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null && url.contains("gemini.google.com") && !url.contains("accounts.google.com")) {
                                cm.flush()
                                val cookies = cm.getCookie("https://gemini.google.com") ?: ""
                                if (cookies.contains("__Secure-1PSID=")) {
                                    authRepository.saveSessionFromCookies(cookies)
                                }
                                view?.evaluateJavascript(
                                    "(function(){ var w = window.WIZ_global_data; return w ? JSON.stringify({SNlM0e: w.SNlM0e || w.thykhd || '', FdrFJe: w.FdrFJe || '', cfb2h: w.cfb2h || '', qKIAYe: w.qKIAYe || '', Ylro7b: w.Ylro7b || ''}) : ''; })()"
                                ) { jsRes ->
                                    val ok = !jsRes.isNullOrBlank() && jsRes != "null" && jsRes != "\"\"" && geminiWebClient.applyWizTokensJson(jsRes)
                                    finishWith(ok)
                                }
                            } else if (url != null && url.contains("accounts.google.com") && (url.contains("ServiceLogin") || url.contains("signin"))) {
                                finishWith(false)
                            }
                        }
                    }
                    wv.loadUrl(GeminiWebClient.GEMINI_APP_URL)
                    cont.invokeOnCancellation {
                        try {
                            wv.stopLoading()
                            wv.destroy()
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    finishWith(false)
                }
            }
        } ?: false
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val orig = chain.request()
                val urlStr = orig.url.toString()
                if (urlStr.contains("googleusercontent.com")) {
                    val cookie = authRepository.getCookieString()
                    val req = orig.newBuilder()
                        .addHeader("Cookie", cookie)
                        .addHeader("Origin", "https://gemini.google.com")
                        .addHeader("Referer", "https://gemini.google.com/")
                        .addHeader("User-Agent", GeminiWebClient.USER_AGENT)
                        .build()
                    chain.proceed(req)
                } else {
                    chain.proceed(orig)
                }
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: GeminiApp
            private set
    }
}
