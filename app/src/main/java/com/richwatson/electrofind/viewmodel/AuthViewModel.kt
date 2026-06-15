package com.richwatson.electrofind.viewmodel

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.richwatson.electrofind.auth.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel(private val tokenManager: TokenManager) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(tokenManager.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Called by the WebView login screen once login is detected
    fun onWebViewLoginComplete(url: String) {
        extractAndSaveWebViewCookies()
    }

    // Extract token, refreshToken and csrftoken cookies from the WebView cookie store
    fun extractAndSaveWebViewCookies() {
        val raw = CookieManager.getInstance().getCookie("https://electroverse.com") ?: return
        val cookies = raw.split(";").associate {
            val parts = it.trim().split("=", limit = 2)
            (parts.getOrNull(0)?.trim() ?: "") to (parts.getOrNull(1)?.trim() ?: "")
        }
        cookies["token"]?.takeIf { it.isNotBlank() }?.let { tokenManager.jwtToken = it }
        cookies["refreshToken"]?.takeIf { it.isNotBlank() }?.let { tokenManager.refreshToken = it }
        cookies["csrftoken"]?.takeIf { it.isNotBlank() }?.let { tokenManager.csrfToken = it }
        _isLoggedIn.value = tokenManager.isLoggedIn
    }

    fun logout() {
        tokenManager.clear()
        CookieManager.getInstance().removeAllCookies(null)
        _isLoggedIn.value = false
    }
}
