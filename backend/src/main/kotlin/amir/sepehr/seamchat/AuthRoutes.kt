package amir.sepehr.seamchat

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get

class AuthRoutes {
    private val db = Database()

    fun register(route: Route) {
        route.post("/api/v1/auth/register") {
            val r = call.receive<AuthRequest>()
            val username = r.username.trim()
            if (!Regex("^[A-Za-z0-9_]{3,24}$").matches(username)) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Username must be 3-24 characters using letters, numbers or _."))
            if (r.password.length !in 8..128) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be 8-128 characters."))
            val invite = r.inviteCode?.trim()?.uppercase().orEmpty()
            if (invite.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invite code is required."))
            if (db.userByUsername(username) != null) return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("Username is already taken."))
            if (!db.inviteAvailable(invite)) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid or already used invite code."))
            val (hash,salt) = newPassword(r.password)
            val user = UserRecord(java.util.UUID.randomUUID().toString(), username, r.displayName?.trim().takeUnless { it.isNullOrBlank() } ?: username, hash, salt)
            db.createUser(user)
            db.useInvite(invite,user.id)
            val (token,expires) = db.createSession(user.id)
            call.respond(HttpStatusCode.Created, AuthResponse(user.dto(),token,expires))
        }

        route.post("/api/v1/auth/login") {
            val r = call.receive<AuthRequest>()
            val user = db.userByUsername(r.username.trim())
            if (user == null || !checkPassword(r.password,user.passwordSalt,user.passwordHash)) return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid username or password."))
            val (token,expires) = db.createSession(user.id)
            call.respond(AuthResponse(user.dto(),token,expires))
        }

        route.get("/api/v1/auth/me") {
            val auth = authFromCall(db)
            if (auth == null) return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            call.respond(mapOf("user" to auth.user.dto()))
        }

        route.post("/api/v1/auth/logout") {
            authFromCall(db)?.let { db.revoke(it.sessionId) }
            call.respond(mapOf("ok" to true))
        }
    }
}

private fun authFromCall(db: Database): AuthUser? {
    val h = callHolder.get() ?: return null
    return h.request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()?.let(db::auth)
}

private val callHolder = ThreadLocal<ApplicationCallHolder?>()
private class ApplicationCallHolder(val request: io.ktor.server.application.ApplicationRequest)
