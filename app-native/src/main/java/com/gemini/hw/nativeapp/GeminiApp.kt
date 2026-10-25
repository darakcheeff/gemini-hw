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
        gemsAndSparkRepository = GemsAndSparkRepository()
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
