package com.gemini.hw.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class AuthRepository(private val context: Context? = null) {

    private val prefs: SharedPreferences? by lazy {
        try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    private val _isLoggedIn = MutableStateFlow(false)
    open val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    open val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    init {
        try {
            _isLoggedIn.value = hasValidSession()
            _userEmail.value = prefs?.getString(KEY_USER_EMAIL, null)
        } catch (_: Exception) {}
    }

    open fun hasValidSession(): Boolean {
        val psid = prefs?.getString(KEY_PSID, null)
        val snlm0e = prefs?.getString(KEY_SNLM0E, null)
        return !psid.isNullOrBlank() || !snlm0e.isNullOrBlank()
    }

    open fun getCookieString(): String {
        return prefs?.getString(KEY_COOKIES, "") ?: ""
    }

    open fun getSnlm0eToken(): String? {
        return prefs?.getString(KEY_SNLM0E, null)
    }

    open fun saveSessionFromCookies(cookieString: String, snlm0e: String? = null, email: String? = null) {
        val editor = prefs?.edit() ?: return
        editor.putString(KEY_COOKIES, cookieString)

        val cookies = parseCookieHeader(cookieString)
        cookies["__Secure-1PSID"]?.let { editor.putString(KEY_PSID, it) }
        cookies["__Secure-1PSIDTS"]?.let { editor.putString(KEY_PSIDTS, it) }
        cookies["SAPISID"]?.let { editor.putString(KEY_SAPISID, it) }

        if (!snlm0e.isNullOrBlank()) {
            editor.putString(KEY_SNLM0E, snlm0e)
        }
        if (!email.isNullOrBlank()) {
            editor.putString(KEY_USER_EMAIL, email)
            _userEmail.value = email
        }

        editor.apply()
        _isLoggedIn.value = true
    }

    open fun saveSnlm0eToken(token: String) {
        prefs?.edit()?.putString(KEY_SNLM0E, token)?.apply()
        _isLoggedIn.value = hasValidSession()
    }

    open fun syncFromSystemCookieManager() {
        try {
            val cookieManager = CookieManager.getInstance()
            val googleCookies = cookieManager.getCookie("https://gemini.google.com")
            if (!googleCookies.isNullOrBlank()) {
                saveSessionFromCookies(googleCookies)
            }
        } catch (_: Exception) {}
    }

    open fun logout() {
        prefs?.edit()?.clear()?.apply()
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        _isLoggedIn.value = false
        _userEmail.value = null
    }

    private fun parseCookieHeader(cookieStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        cookieStr.split(";").forEach { part ->
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                map[key] = value
            }
        }
        return map
    }

    companion object {
        private const val PREFS_NAME = "gemini_auth_session"
        private const val KEY_COOKIES = "cookies_raw"
        private const val KEY_PSID = "psid"
        private const val KEY_PSIDTS = "psidts"
        private const val KEY_SAPISID = "sapisid"
        private const val KEY_SNLM0E = "snlm0e_token"
        private const val KEY_USER_EMAIL = "user_email"

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
