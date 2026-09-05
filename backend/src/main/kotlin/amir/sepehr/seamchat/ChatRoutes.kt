package amir.sepehr.seamchat

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import java.util.concurrent.ConcurrentHashMap

class ChatRoutes(private val db: Database) {
    private val sockets = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()
    private val onlineUsers = ConcurrentHashMap<String, MutableSet<String>>()

    private suspend fun broadcast(conversationId: String, payload: Any, except: DefaultWebSocketServerSession? = null) {
        sockets[conversationId]?.toList()?.forEach { session ->
            if (session != except) runCatching { session.sendSerialized(payload) }
        }
    }

    fun register(route: Route) {
        route.get("/api/v1/users/search") {
            val auth = authFromCall(call, db) ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            if (query.length < 2) return@get call.respond(emptyList<UserDto>())
            call.respond(db.searchUsers(query, auth.user.id))
        }
        route.get("/api/v1/conversations") {
            val auth = authFromCall(call, db) ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            call.respond(db.conversations(auth.user.id))
        }
        route.post("/api/v1/conversations/direct") {
            val auth = authFromCall(call, db) ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            val request = call.receive<CreateDirectConversationRequest>()
            try {
                call.respond(HttpStatusCode.Created, db.createDirectConversation(auth.user.id, request.userId))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid conversation"))
            }
        }
        route.post("/api/v1/conversations/{id}/read") {
            val auth = authFromCall(call, db) ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing conversation id"))
            if (!db.isMember(id, auth.user.id)) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not a conversation member"))
            db.markRead(id, auth.user.id)
            broadcast(id, RealtimeEvent("read", auth.user.id))
            call.respond(mapOf("ok" to true))
        }
        route.get("/api/v1/conversations/{id}/messages") {
            val auth = authFromCall(call, db) ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing conversation id"))
            if (!db.isMember(id, auth.user.id)) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not a conversation member"))
            call.respond(db.messages(id))
        }
        route.post("/api/v1/conversations/{id}/messages") {
            val auth = authFromCall(call, db) ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing conversation id"))
            if (!db.isMember(id, auth.user.id)) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not a conversation member"))
            val request = call.receive<SendMessageRequest>()
            if (request.body.isBlank() || request.body.length > 10000) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Message must be 1-10000 characters."))
            val type = request.type.lowercase().takeIf { it in setOf("text", "image", "video", "audio", "file") } ?: "text"
            val message = db.insertMessage(id, auth.user.id, request.body.trim(), type)
            broadcast(id, RealtimeEvent("message", auth.user.id, messageId = message.id, body = message.body, createdAt = message.createdAt))
            call.respond(HttpStatusCode.Created, message)
        }
        route.webSocket("/api/v1/realtime/{conversationId}") {
            val auth = authFromCall(call, db)
            val conversationId = call.parameters["conversationId"]
            if (auth == null || conversationId == null || !db.isMember(conversationId, auth.user.id)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized")); return@webSocket
            }
            val userId = auth.user.id
            val set = sockets.computeIfAbsent(conversationId) { ConcurrentHashMap.newKeySet() }
            val users = onlineUsers.computeIfAbsent(conversationId) { ConcurrentHashMap.newKeySet() }
            set.add(this); users.add(userId)
            broadcast(conversationId, RealtimeEvent("presence", userId, online = true), except = this)
            sendSerialized(RealtimeEvent("presence_snapshot", userId, onlineUsers = users.toList()))
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText().trim()
                        when {
                            text == "ping" -> send("pong")
                            text.startsWith("{\"type\":\"typing\"") -> {
                                val value = text.contains("\"value\":true")
                                broadcast(conversationId, RealtimeEvent("typing", userId, value = value), except = this)
                            }
                            text.startsWith("{\"type\":\"read\"") -> {
                                val messageId = Regex("\\\"messageId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)
                                if (messageId != null) broadcast(conversationId, RealtimeEvent("read", userId, messageId = messageId), except = this)
                            }
                        }
                    }
                }
            } finally {
                set.remove(this); users.remove(userId)
                if (set.isEmpty()) sockets.remove(conversationId, set)
                if (users.isEmpty()) onlineUsers.remove(conversationId, users)
                broadcast(conversationId, RealtimeEvent("presence", userId, online = false), except = this)
            }
        }
    }
}

data class RealtimeEvent(
    val type: String,
    val userId: String,
    val messageId: String? = null,
    val body: String? = null,
    val createdAt: Long? = null,
    val value: Boolean? = null,
    val online: Boolean? = null,
    val onlineUsers: List<String>? = null
)
