package com.gemini.hw.nativeapp

import android.app.Application
import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.network.GeminiWebClient
import com.gemini.hw.core.repository.GemsAndSparkRepository

class GeminiApp : Application() {

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

    companion object {
        lateinit var instance: GeminiApp
            private set
    }
}
