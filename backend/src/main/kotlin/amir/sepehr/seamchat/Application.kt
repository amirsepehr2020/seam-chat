package amir.sepehr.seamchat

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val db = Database()
    install(ContentNegotiation) { json() }
    install(CORS) { anyHost(); allowHeader("Authorization"); allowHeader("Content-Type") }
    install(WebSockets)
    routing {
        get("/health") { call.respond(mapOf("ok" to true, "service" to "seam-chat-api", "version" to "1.0")) }
        get("/") { call.respond(mapOf("name" to "SEAM CHAT API", "version" to "v1", "status" to "online")) }
        AuthRoutes(db).register(this)
        ChatRoutes(db).register(this)
        MediaRoutes(db).register(this)
    }
}
