package amir.sepehr.seamchat.chat

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatApi {
    @GET("api/v1/conversations/{id}/messages")
    suspend fun messages(@Path("id") conversationId: String): Response<List<MessageDto>>

    @POST("api/v1/conversations/{id}/messages")
    suspend fun send(@Path("id") conversationId: String, @Body request: SendMessageRequest): Response<MessageDto>
}

data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String?,
    val type: String,
    val createdAt: Long
)

data class SendMessageRequest(val body: String, val type: String = "text")
