package amir.sepehr.seamchat

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import java.util.concurrent.ConcurrentHashMap

class ChatRoutes(private val db: Database) {
    private val sockets = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    fun register(route: Route) {
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
            if (request.body.length !in 1..10000) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Message must be 1-10000 characters."))
            val message = db.insertMessage(id, auth.user.id, request.body, request.type)
            sockets[id]?.toList()?.forEach { session -> runCatching { session.sendSerialized(message) } }
            call.respond(HttpStatusCode.Created, message)
        }

        route.webSocket("/api/v1/realtime/{conversationId}") {
            val auth = authFromCall(call, db)
            val conversationId = call.parameters["conversationId"]
            if (auth == null || conversationId == null || !db.isMember(conversationId, auth.user.id)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized")); return@webSocket
            }
            val set = sockets.computeIfAbsent(conversationId) { ConcurrentHashMap.newKeySet() }
            set.add(this)
            try {
                for (frame in incoming) if (frame is Frame.Text && frame.readText() == "ping") send("pong")
            } finally { set.remove(this) }
        }
    }
}
