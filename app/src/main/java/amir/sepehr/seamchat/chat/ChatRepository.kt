package amir.sepehr.seamchat.chat

import android.content.Context
import android.net.Uri
import amir.sepehr.seamchat.data.SeamDatabase
import amir.sepehr.seamchat.network.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api = ApiClient.chat(appContext)
    private val mediaApi = ApiClient.media(appContext)
    private val dao = SeamDatabase.get(appContext).messageDao()

    fun observeCachedMessages(conversationId: String): Flow<List<MessageDto>> =
        dao.observe(conversationId).map { entities -> entities.map(MessageEntity::toDto) }

    suspend fun refreshMessages(conversationId: String): Result<Unit> = runCatching {
        val response = api.messages(conversationId)
        if (!response.isSuccessful) error("Could not load messages (${response.code()})")
        dao.upsertAll(response.body().orEmpty().map(MessageDto::toEntity))
    }

    suspend fun sendMessage(conversationId: String, body: String, type: String = "text"): Result<MessageDto> = runCatching {
        val response = api.send(conversationId, SendMessageRequest(body, type))
        if (!response.isSuccessful) error("Could not send message (${response.code()})")
        val message = response.body() ?: error("Server returned no message")
        dao.upsert(message.toEntity())
        message
    }

    suspend fun uploadMedia(uri: Uri, type: String): Result<MediaUploadResponse> = runCatching {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read selected file")
        val body = bytes.toRequestBody(mime.toMediaType())
        val filename = "seam_${System.currentTimeMillis()}"
        val part = MultipartBody.Part.createFormData("file", filename, body)
        val response = mediaApi.upload(part, type.toRequestBody("text/plain".toMediaType()))
        if (!response.isSuccessful) error("Upload failed (${response.code()})")
        response.body() ?: error("Server returned no media")
    }

    suspend fun cache(message: MessageDto) = dao.upsert(message.toEntity())
}
