package com.richwatson.electrofind.viewmodel

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richwatson.electrofind.auth.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val tokenManager: TokenManager) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(tokenManager.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // True when the API rejected our token — shown as a banner on the login screen
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        viewModelScope.launch {
            tokenManager.authFailures.collect {
                tokenManager.jwtToken = null
                _sessionExpired.value = true
                _isLoggedIn.value = false
            }
        }
    }

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
        if (tokenManager.isLoggedIn) _sessionExpired.value = false
        _isLoggedIn.value = tokenManager.isLoggedIn
    }

    fun logout() {
        tokenManager.clear()
        CookieManager.getInstance().removeAllCookies(null)
        _isLoggedIn.value = false
    }
}
