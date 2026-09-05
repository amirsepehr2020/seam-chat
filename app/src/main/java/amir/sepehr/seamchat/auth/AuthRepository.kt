package amir.sepehr.seamchat.auth

import android.content.Context
import amir.sepehr.seamchat.network.ApiClient
import amir.sepehr.seamchat.network.LoginRequest
import amir.sepehr.seamchat.network.RegisterRequest
import amir.sepehr.seamchat.network.SessionStore

class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api = ApiClient.auth(appContext)
    private val sessionStore = SessionStore(appContext)

    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val response = api.login(LoginRequest(username, password))
        if (!response.isSuccessful) error("Login failed (${response.code()})")
        val token = response.body()?.token ?: error("Server returned no session token")
        sessionStore.save(token)
    }

    suspend fun register(
        username: String,
        password: String,
        inviteCode: String,
        displayName: String = username
    ): Result<Unit> = runCatching {
        val response = api.register(RegisterRequest(username, password, inviteCode, displayName))
        if (!response.isSuccessful) error("Registration failed (${response.code()})")
        val token = response.body()?.token ?: error("Server returned no session token")
        sessionStore.save(token)
    }

    suspend fun hasSession(): Boolean = runCatching {
        val response = api.me()
        response.isSuccessful && response.body()?.user != null
    }.getOrDefault(false)

    suspend fun logout() {
        runCatching { api.logout() }
        sessionStore.clear()
    }
}
