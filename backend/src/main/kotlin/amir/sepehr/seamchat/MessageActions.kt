package amir.sepehr.seamchat

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID

/** Persistent message actions. Kept separate from the main database facade so the existing schema remains compatible. */
class MessageActions {
    private val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/seam_chat"
        username = System.getenv("DATABASE_USER") ?: "seam"
        password = System.getenv("DATABASE_PASSWORD") ?: "seam"
        maximumPoolSize = 5
    })

    init { pool.connection.use { c -> c.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS message_reactions(message_id TEXT REFERENCES messages(id) ON DELETE CASCADE,user_id TEXT REFERENCES users(id) ON DELETE CASCADE,reaction TEXT NOT NULL,created_at BIGINT NOT NULL,PRIMARY KEY(message_id,user_id,reaction))") } } }

    fun edit(messageId: String, userId: String, body: String): MessageDto? = pool.connection.use { c ->
        c.prepareStatement("UPDATE messages SET body=?, edited_at=? WHERE id=? AND sender_id=? AND deleted_at IS NULL RETURNING id,conversation_id,sender_id,body,type,created_at,edited_at").use { p ->
            p.setString(1, body.trim()); p.setLong(2, System.currentTimeMillis()); p.setString(3, messageId); p.setString(4, userId)
            p.executeQuery().use { r -> if (!r.next()) null else MessageDto(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getLong(7),false) }
        }
    }

    fun delete(messageId: String, userId: String): Boolean = pool.connection.use { c ->
        c.prepareStatement("UPDATE messages SET deleted_at=? WHERE id=? AND sender_id=? AND deleted_at IS NULL").use { p -> p.setLong(1,System.currentTimeMillis());p.setString(2,messageId);p.setString(3,userId);p.executeUpdate()==1 }
    }

    fun forward(messageId: String, userId: String, targetConversationId: String): MessageDto? = pool.connection.use { c ->
        c.prepareStatement("SELECT body,type FROM messages WHERE id=? AND deleted_at IS NULL").use { p -> p.setString(1,messageId);p.executeQuery().use { r ->
            if (!r.next()) return@use null
            val id=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
            c.prepareStatement("INSERT INTO messages(id,conversation_id,sender_id,body,type,created_at) VALUES(?,?,?,?,?,?)").use { q -> q.setString(1,id);q.setString(2,targetConversationId);q.setString(3,userId);q.setString(4,r.getString(1));q.setString(5,r.getString(2));q.setLong(6,now);q.executeUpdate() }
            MessageDto(id,targetConversationId,userId,r.getString(1),r.getString(2),now)
        } }
    }

    fun react(messageId:String,userId:String,reaction:String):Boolean = pool.connection.use { c ->
        c.prepareStatement("INSERT INTO message_reactions(message_id,user_id,reaction,created_at) VALUES(?,?,?,?) ON CONFLICT DO NOTHING").use { p -> p.setString(1,messageId);p.setString(2,userId);p.setString(3,reaction.take(32));p.setLong(4,System.currentTimeMillis());p.executeUpdate()>0 }
    }

    fun unreact(messageId:String,userId:String,reaction:String):Boolean = pool.connection.use { c ->
        c.prepareStatement("DELETE FROM message_reactions WHERE message_id=? AND user_id=? AND reaction=?").use { p -> p.setString(1,messageId);p.setString(2,userId);p.setString(3,reaction);p.executeUpdate()>0 }
    }
}
