package amir.sepehr.seamchat.network

import android.content.Context
import amir.sepehr.seamchat.BuildConfig
import amir.sepehr.seamchat.chat.ChatApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            sessionStore.token()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }.build()
        return chain.proceed(request)
    }
}

object ApiClient {
    private fun retrofit(context: Context): Retrofit {
        val store = SessionStore(context.applicationContext)
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun auth(context: Context): AuthApi = retrofit(context).create(AuthApi::class.java)
    fun chat(context: Context): ChatApi = retrofit(context).create(ChatApi::class.java)
    fun conversations(context: Context): ConversationApi = retrofit(context).create(ConversationApi::class.java)
}
