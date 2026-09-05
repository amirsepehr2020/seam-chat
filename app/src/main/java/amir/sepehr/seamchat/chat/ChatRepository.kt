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
    private val appContext=context.applicationContext; private val api=ApiClient.chat(appContext); private val mediaApi=ApiClient.media(appContext); private val dao=SeamDatabase.get(appContext).messageDao()
    fun observeCachedMessages(id:String):Flow<List<MessageDto>>=dao.observe(id).map{it.map(MessageEntity::toDto)}
    suspend fun refreshMessages(id:String):Result<Unit>=runCatching{val r=api.messages(id);if(!r.isSuccessful)error("Could not load messages (${r.code()})");dao.upsertAll(r.body().orEmpty().map(MessageDto::toEntity))}
    suspend fun sendMessage(id:String,body:String,type:String="text"):Result<MessageDto>=runCatching{val r=api.send(id,SendMessageRequest(body,type));if(!r.isSuccessful)error("Could not send message (${r.code()})");val m=r.body()?:error("Server returned no message");dao.upsert(m.toEntity());m}
    suspend fun uploadMedia(uri:Uri,type:String):Result<MediaUploadResponse>=runCatching{val resolver=appContext.contentResolver;val mime=resolver.getType(uri)?:"application/octet-stream";val bytes=resolver.openInputStream(uri)?.use{it.readBytes()}?:error("Could not read selected file");val part=MultipartBody.Part.createFormData("file","seam_${System.currentTimeMillis()}",bytes.toRequestBody(mime.toMediaType()));val r=mediaApi.upload(part,type.toRequestBody("text/plain".toMediaType()));if(!r.isSuccessful)error("Upload failed (${r.code()})");r.body()?:error("Server returned no media")}
    suspend fun editMessage(messageId:String,body:String):Result<MessageDto>=runCatching{val r=api.edit(messageId,EditMessageRequest(body));if(!r.isSuccessful)error("Edit failed (${r.code()})");r.body()?:error("Server returned no message")}
    suspend fun deleteMessage(messageId:String):Result<Unit>=runCatching{val r=api.delete(messageId);if(!r.isSuccessful)error("Delete failed (${r.code()})")}
    suspend fun forwardMessage(messageId:String,targetConversationId:String):Result<MessageDto>=runCatching{val r=api.forward(messageId,ForwardMessageRequest(targetConversationId));if(!r.isSuccessful)error("Forward failed (${r.code()})");r.body()?:error("Server returned no message")}
    suspend fun react(messageId:String,reaction:String):Result<Unit>=runCatching{val r=api.react(messageId,ReactionRequest(reaction));if(!r.isSuccessful)error("Reaction failed (${r.code()})")}
    suspend fun unreact(messageId:String,reaction:String):Result<Unit>=runCatching{val r=api.unreact(messageId,ReactionRequest(reaction));if(!r.isSuccessful)error("Reaction removal failed (${r.code()})")}
    suspend fun cache(message:MessageDto)=dao.upsert(message.toEntity())
}
