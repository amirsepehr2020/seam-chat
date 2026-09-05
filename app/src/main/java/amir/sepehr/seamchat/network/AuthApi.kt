package amir.sepehr.seamchat.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<MeResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}

data class RegisterRequest(
    val username: String,
    val password: String,
    val inviteCode: String,
    val displayName: String = username
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val user: UserDto? = null,
    val token: String? = null,
    val expiresAt: Long? = null
)

data class MeResponse(
    val user: UserDto? = null
)

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null
)
