package amir.sepehr.seamchat.chat

import android.content.Context
import amir.sepehr.seamchat.data.SeamDatabase
import amir.sepehr.seamchat.network.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api = ApiClient.chat(appContext)
    private val dao = SeamDatabase.get(appContext).messageDao()

    fun observeCachedMessages(conversationId: String): Flow<List<MessageDto>> =
        dao.observe(conversationId).map { entities -> entities.map(MessageEntity::toDto) }

    suspend fun refreshMessages(conversationId: String): Result<Unit> = runCatching {
        val response = api.messages(conversationId)
        if (!response.isSuccessful) error("Could not load messages (${response.code()})")
        dao.upsertAll(response.body().orEmpty().map(MessageDto::toEntity))
    }

    suspend fun sendMessage(conversationId: String, body: String): Result<MessageDto> = runCatching {
        val response = api.send(conversationId, body.let { SendMessageRequest(it) })
        if (!response.isSuccessful) error("Could not send message (${response.code()})")
        val message = response.body() ?: error("Server returned no message")
        dao.upsert(message.toEntity())
        message
    }

    suspend fun cache(message: MessageDto) = dao.upsert(message.toEntity())
}
