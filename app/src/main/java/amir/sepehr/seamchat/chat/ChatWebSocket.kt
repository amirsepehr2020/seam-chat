package amir.sepehr.seamchat.chat

import android.content.Context
import amir.sepehr.seamchat.BuildConfig
import amir.sepehr.seamchat.network.SessionStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.google.gson.Gson

sealed interface SocketEvent {
    data class Connected(val value: Boolean) : SocketEvent
    data class Message(val value: MessageDto) : SocketEvent
    data class Failed(val error: Throwable) : SocketEvent
}

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
            close()
            return@callbackFlow
        }
        val base = BuildConfig.API_BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
        val request = Request.Builder()
            .url("$base/api/v1/realtime/$conversationId")
            .header("Authorization", "Bearer $token")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(SocketEvent.Connected(true))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { gson.fromJson(text, MessageDto::class.java) }
                    .onSuccess { trySend(SocketEvent.Message(it)) }
                    .onFailure { trySend(SocketEvent.Failed(it)) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(SocketEvent.Failed(t))
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(SocketEvent.Connected(false))
                close()
            }
        })

        awaitClose {
            socket?.close(1000, "Leaving conversation")
            socket = null
        }
    }

    fun ping(): Boolean = socket?.send("ping") ?: false
    fun close() { socket?.close(1000, "Closed"); socket = null }
}
