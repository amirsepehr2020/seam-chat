package amir.sepehr.seamchat.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import amir.sepehr.seamchat.data.SeamDatabase
import amir.sepehr.seamchat.network.ApiClient
import java.io.ByteArrayOutputStream
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

    fun observeCachedMessages(id: String): Flow<List<MessageDto>> =
        dao.observe(id).map { it.map(MessageEntity::toDto) }

    suspend fun refreshMessages(id: String): Result<Unit> = runCatching {
        val response = api.messages(id)
        if (!response.isSuccessful) error("Could not load messages (${response.code()})")
        dao.upsertAll(response.body().orEmpty().map(MessageDto::toEntity))
    }

    suspend fun sendMessage(id: String, body: String, type: String = "text"): Result<MessageDto> = runCatching {
        val response = api.send(id, SendMessageRequest(body, type))
        if (!response.isSuccessful) error("Could not send message (${response.code()})")
        val message = response.body() ?: error("Server returned no message")
        dao.upsert(message.toEntity())
        message
    }

    suspend fun reply(messageId: String, body: String, type: String = "text"): Result<MessageDto> = runCatching {
        val response = api.reply(messageId, ReplyMessageRequest(body, type))
        if (!response.isSuccessful) error("Reply failed (${response.code()})")
        val message = response.body() ?: error("Server returned no message")
        dao.upsert(message.toEntity())
        message
    }

    suspend fun uploadMedia(uri: Uri, type: String): Result<MediaUploadResponse> = runCatching {
        val resolver = appContext.contentResolver
        val originalMime = resolver.getType(uri) ?: "application/octet-stream"
        val payload = if (type == "image") {
            compressImage(uri)
        } else {
            resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read selected file")
        }
        val mime = if (type == "image") "image/jpeg" else originalMime
        val extension = if (type == "image") ".jpg" else ""
        val filename = "seam_${System.currentTimeMillis()}$extension"
        val part = MultipartBody.Part.createFormData(
            "file",
            filename,
            payload.toRequestBody(mime.toMediaType())
        )
        val response = mediaApi.upload(part, type.toRequestBody("text/plain".toMediaType()))
        if (!response.isSuccessful) error("Upload failed (${response.code()})")
        response.body() ?: error("Server returned no media")
    }

    private fun compressImage(uri: Uri): ByteArray {
        val input = appContext.contentResolver.openInputStream(uri) ?: error("Could not read image")
        val source = BitmapFactory.decodeStream(input) ?: error("Invalid image")
        input.close()
        val maxDimension = 2048f
        val scale = minOf(1f, maxDimension / source.width, maxDimension / source.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else source
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) error("Image compression failed")
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        return output.toByteArray()
    }

    suspend fun editMessage(id: String, body: String): Result<MessageDto> = runCatching {
        val response = api.edit(id, EditMessageRequest(body))
        if (!response.isSuccessful) error("Edit failed (${response.code()})")
        val message = response.body() ?: error("Server returned no message")
        dao.upsert(message.toEntity())
        message
    }

    suspend fun deleteMessage(id: String): Result<Unit> = runCatching {
        val response = api.delete(id)
        if (!response.isSuccessful) error("Delete failed (${response.code()})")
    }

    suspend fun forwardMessage(id: String, target: String): Result<MessageDto> = runCatching {
        val response = api.forward(id, ForwardMessageRequest(target))
        if (!response.isSuccessful) error("Forward failed (${response.code()})")
        val message = response.body() ?: error("Server returned no message")
        dao.upsert(message.toEntity())
        message
    }

    suspend fun react(id: String, reaction: String): Result<Unit> = runCatching {
        val response = api.react(id, ReactionRequest(reaction))
        if (!response.isSuccessful) error("Reaction failed (${response.code()})")
    }

    suspend fun unreact(id: String, reaction: String): Result<Unit> = runCatching {
        val response = api.unreact(id, ReactionRequest(reaction))
        if (!response.isSuccessful) error("Reaction removal failed (${response.code()})")
    }

    suspend fun cache(message: MessageDto) = dao.upsert(message.toEntity())
}
