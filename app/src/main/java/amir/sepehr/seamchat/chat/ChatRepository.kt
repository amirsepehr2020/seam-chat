package amir.sepehr.seamchat.chat

import android.content.Context
import amir.sepehr.seamchat.network.ApiClient

class ChatRepository(context: Context) {
    private val api = ApiClient.chat(context.applicationContext)

    suspend fun loadMessages(conversationId: String): Result<List<MessageDto>> = runCatching {
        val response = api.messages(conversationId)
        if (!response.isSuccessful) error("Could not load messages (${response.code()})")
        response.body().orEmpty()
    }

    suspend fun sendMessage(conversationId: String, body: String): Result<MessageDto> = runCatching {
        val response = api.send(conversationId, SendMessageRequest(body))
        if (!response.isSuccessful) error("Could not send message (${response.code()})")
        response.body() ?: error("Server returned no message")
    }
}
