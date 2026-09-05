package amir.sepehr.seamchat

import java.sql.DriverManager

/** Seeds the initial single-use beta invite codes into the running PostgreSQL database. */
fun seedInitialInvites() {
    val url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/seam_chat"
    val user = System.getenv("DATABASE_USER") ?: "seam"
    val password = System.getenv("DATABASE_PASSWORD") ?: "seam"
    val codes = listOf(
        "SEAM-7K4P-9X2M",
        "SEAM-3Q8N-5R7T",
        "SEAM-6V2L-8C9H",
        "SEAM-4D7W-2P6Y",
        "SEAM-9M3F-7J5K",
        "SEAM-2H8R-4N6V",
        "SEAM-5T9B-3X7Q",
        "SEAM-8P4C-6L2D",
        "SEAM-1Y7G-9W3S",
        "SEAM-6N5Z-2K8M"
    )
    DriverManager.getConnection(url, user, password).use { connection ->
        connection.prepareStatement(
            "INSERT INTO invite_codes(code, created_at) VALUES (?, ?) ON CONFLICT (code) DO NOTHING"
        ).use { statement ->
            val now = System.currentTimeMillis()
            codes.forEach { code ->
                statement.setString(1, code)
                statement.setLong(2, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
