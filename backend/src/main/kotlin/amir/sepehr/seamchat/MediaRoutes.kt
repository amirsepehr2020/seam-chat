package amir.sepehr.seamchat

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.io.File
import java.util.UUID

class MediaRoutes(private val db: Database) {
    private val root = File(System.getenv("MEDIA_DIR") ?: "data/media").apply { mkdirs() }
    private val maxBytes = (System.getenv("MEDIA_MAX_BYTES")?.toLongOrNull() ?: 25L * 1024 * 1024).coerceAtMost(50L * 1024 * 1024)

    fun register(route: Route) {
        route.post("/api/v1/media/upload") {
            val auth = authFromCall(call, db) ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            var saved: File? = null
            var mediaType = "file"
            var originalName: String? = null
            try {
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FormItem && part.name == "type") {
                        mediaType = part.value.lowercase().takeIf { it in setOf("image", "video", "audio", "file") } ?: "file"
                    } else if (part is PartData.FileItem && part.name == "file") {
                        originalName = part.originalFileName?.take(120)
                        val extension = originalName?.substringAfterLast('.', "")?.lowercase()?.filter { it.isLetterOrDigit() }?.take(10).orEmpty()
                        val id = UUID.randomUUID().toString()
                        val target = File(root, if (extension.isBlank()) id else "$id.$extension")
                        var total = 0L
                        part.provider().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    total += read
                                    if (total > maxBytes) throw IllegalArgumentException("File is too large")
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        saved = target
                    }
                    part.dispose()
                }
                val file = saved ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing file"))
                val base = System.getenv("PUBLIC_BASE_URL")?.trimEnd('/') ?: "http://localhost:8080"
                call.respond(HttpStatusCode.Created, MediaUploadResponse(file.name, "$base/api/v1/media/${file.name}", mediaType, originalName))
            } catch (e: IllegalArgumentException) {
                saved?.delete()
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid upload"))
            } catch (e: Exception) {
                saved?.delete()
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Upload failed"))
            }
        }

        route.get("/api/v1/media/{name}") {
            val auth = authFromCall(call, db) ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing media name"))
            if (name.contains("..") || name.contains('/') || name.contains('\\')) return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid media name"))
            val file = File(root, name).canonicalFile
            if (!file.path.startsWith(root.canonicalFile.path + File.separator) || !file.isFile) return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Media not found"))
            call.respondFile(file, contentType = ContentType.Application.OctetStream)
        }
    }
}

data class MediaUploadResponse(
    val id: String,
    val url: String,
    val type: String,
    val originalName: String?
)
