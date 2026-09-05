package amir.sepehr.seamchat.chat

import android.content.Context
import amir.sepehr.seamchat.BuildConfig
import amir.sepehr.seamchat.network.SessionStore
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface SocketEvent {
    data class Connected(val value: Boolean) : SocketEvent
    data class Message(val value: MessageDto) : SocketEvent
    data class Typing(val userId: String, val value: Boolean) : SocketEvent
    data class Presence(val userId: String, val online: Boolean) : SocketEvent
    data class Read(val userId: String, val messageId: String) : SocketEvent
    data class Failed(val error: Throwable) : SocketEvent
}

data class RealtimeEventDto(
    val type: String,
    val userId: String,
    val messageId: String? = null,
    val body: String? = null,
    val createdAt: Long? = null,
    val value: Boolean? = null,
    val online: Boolean? = null,
    val onlineUsers: List<String>? = null
)

class ChatWebSocket(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder().build()
    private val store = SessionStore(appContext)
    private val gson = Gson()
    private var socket: WebSocket? = null

    fun connect(conversationId: String): Flow<SocketEvent> = callbackFlow {
        val token = store.token()
        if (token.isNullOrBlank()) {
            trySend(SocketEvent.Failed(IllegalStateException("No active session")))
            close(); return@callbackFlow
        }
        val base = BuildConfig.API_BASE_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://").trimEnd('/')
        val request = Request.Builder().url("$base/api/v1/realtime/$conversationId").header("Authorization", "Bearer $token").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { trySend(SocketEvent.Connected(true)) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val event = gson.fromJson(text, RealtimeEventDto::class.java)
                    when (event.type) {
                        "message" -> event.messageId?.let { MessageDto(it, conversationId, event.userId, event.body, "text", event.createdAt ?: System.currentTimeMillis()) }?.let { trySend(SocketEvent.Message(it)) }
                        "typing" -> trySend(SocketEvent.Typing(event.userId, event.value == true))
                        "presence" -> trySend(SocketEvent.Presence(event.userId, event.online == true))
                        "read" -> event.messageId?.let { trySend(SocketEvent.Read(event.userId, it)) }
                    }
                }.onFailure { trySend(SocketEvent.Failed(it)) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { trySend(SocketEvent.Failed(t)); close(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { trySend(SocketEvent.Connected(false)); close() }
        })
        awaitClose { socket?.close(1000, "Leaving conversation"); socket = null }
    }

    fun ping(): Boolean = socket?.send("ping") ?: false
    fun setTyping(value: Boolean): Boolean = socket?.send(gson.toJson(mapOf("type" to "typing", "value" to value))) ?: false
    fun markRead(messageId: String): Boolean = socket?.send(gson.toJson(mapOf("type" to "read", "messageId" to messageId))) ?: false
    fun close() { socket?.close(1000, "Closed"); socket = null }
}
