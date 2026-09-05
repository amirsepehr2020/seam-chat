package amir.sepehr.seamchat

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class UserRecord(val id: String, val username: String, val displayName: String, val passwordHash: String, val passwordSalt: String, val avatarUrl: String? = null) {
    fun dto() = UserDto(id, username, displayName, avatarUrl)
}

data class AuthUser(val user: UserRecord, val sessionId: String)

class Database {
    private val pool: HikariDataSource
    private val random = SecureRandom()

    init {
        val cfg = HikariConfig().apply {
            jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/seam_chat"
            username = System.getenv("DATABASE_USER") ?: "seam"
            password = System.getenv("DATABASE_PASSWORD") ?: "seam"
            maximumPoolSize = 10
        }
        pool = HikariDataSource(cfg)
        migrate()
    }

    private fun <T> query(block: (Connection) -> T): T = pool.connection.use(block)

    private fun migrate() = query { c ->
        c.createStatement().use { it.execute("""
            CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, username TEXT UNIQUE NOT NULL, display_name TEXT NOT NULL, password_hash TEXT NOT NULL, password_salt TEXT NOT NULL, avatar_url TEXT, created_at BIGINT NOT NULL);
            CREATE TABLE IF NOT EXISTS invite_codes(code TEXT PRIMARY KEY, used_by TEXT REFERENCES users(id) ON DELETE SET NULL, created_at BIGINT NOT NULL, used_at BIGINT);
            CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY, user_id TEXT REFERENCES users(id) ON DELETE CASCADE, token_hash TEXT UNIQUE NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, revoked_at BIGINT);
            CREATE TABLE IF NOT EXISTS conversations(id TEXT PRIMARY KEY, title TEXT, type TEXT NOT NULL DEFAULT 'direct', created_at BIGINT NOT NULL);
            CREATE TABLE IF NOT EXISTS conversation_members(conversation_id TEXT REFERENCES conversations(id) ON DELETE CASCADE, user_id TEXT REFERENCES users(id) ON DELETE CASCADE, joined_at BIGINT NOT NULL, PRIMARY KEY(conversation_id,user_id));
            CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY, conversation_id TEXT REFERENCES conversations(id) ON DELETE CASCADE, sender_id TEXT REFERENCES users(id) ON DELETE CASCADE, body TEXT, type TEXT NOT NULL DEFAULT 'text', created_at BIGINT NOT NULL, edited_at BIGINT, deleted_at BIGINT);
            CREATE TABLE IF NOT EXISTS conversation_reads(conversation_id TEXT REFERENCES conversations(id) ON DELETE CASCADE, user_id TEXT REFERENCES users(id) ON DELETE CASCADE, last_read_at BIGINT NOT NULL, PRIMARY KEY(conversation_id,user_id));
            CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages(conversation_id, created_at DESC);
            CREATE INDEX IF NOT EXISTS idx_conversation_members_user ON conversation_members(user_id, conversation_id);
        """.trimIndent()) }
    }

    private fun userFromRow(r: java.sql.ResultSet) = UserRecord(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6))

    fun userByUsername(name: String): UserRecord? = query { c ->
        c.prepareStatement("SELECT id,username,display_name,password_hash,password_salt,avatar_url FROM users WHERE username=?").use { p ->
            p.setString(1, name); p.executeQuery().use { r -> if (!r.next()) null else userFromRow(r) }
        }
    }

    fun searchUsers(query: String, excludeUserId: String): List<UserDto> = query { c ->
        c.prepareStatement("SELECT id,username,display_name,avatar_url FROM users WHERE id<>? AND (username ILIKE ? OR display_name ILIKE ?) ORDER BY username LIMIT 20").use { p ->
            val q = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
            p.setString(1, excludeUserId); p.setString(2, q); p.setString(3, q)
            p.executeQuery().use { r -> buildList { while (r.next()) add(UserDto(r.getString(1), r.getString(2), r.getString(3), r.getString(4))) } }
        }
    }

    fun user(id: String): UserRecord? = query { c ->
        c.prepareStatement("SELECT id,username,display_name,password_hash,password_salt,avatar_url FROM users WHERE id=?").use { p ->
            p.setString(1,id); p.executeQuery().use { r -> if (!r.next()) null else userFromRow(r) }
        }
    }

    fun createUser(user: UserRecord) = query { c -> c.prepareStatement("INSERT INTO users(id,username,display_name,password_hash,password_salt,avatar_url,created_at) VALUES(?,?,?,?,?,?,?)").use { p ->
        p.setString(1,user.id);p.setString(2,user.username);p.setString(3,user.displayName);p.setString(4,user.passwordHash);p.setString(5,user.passwordSalt);p.setString(6,user.avatarUrl);p.setLong(7,System.currentTimeMillis());p.executeUpdate()
    } }

    fun inviteAvailable(code: String): Boolean = query { c -> c.prepareStatement("SELECT 1 FROM invite_codes WHERE code=? AND used_by IS NULL").use { p -> p.setString(1,code);p.executeQuery().use { it.next() } } }
    fun useInvite(code:String,userId:String)=query{c->c.prepareStatement("UPDATE invite_codes SET used_by=?,used_at=? WHERE code=? AND used_by IS NULL").use{p->p.setString(1,userId);p.setLong(2,System.currentTimeMillis());p.setString(3,code);p.executeUpdate()}}

    fun createSession(userId:String):Pair<String,Long>{
        val token=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes)); val expires=System.currentTimeMillis()+30L*86400000L
        query{c->c.prepareStatement("INSERT INTO sessions(id,user_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?)").use{p->p.setString(1,UUID.randomUUID().toString());p.setString(2,userId);p.setString(3,sha256(token));p.setLong(4,System.currentTimeMillis());p.setLong(5,expires);p.executeUpdate()}}
        return token to expires
    }

    fun auth(token:String):AuthUser?=query{c->c.prepareStatement("SELECT s.id,s.user_id FROM sessions s WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>? ").use{p->p.setString(1,sha256(token));p.setLong(2,System.currentTimeMillis());p.executeQuery().use{r->if(!r.next())null else user(r.getString(2))?.let{AuthUser(it,r.getString(1))}}}}
    fun revoke(sessionId:String)=query{c->c.prepareStatement("UPDATE sessions SET revoked_at=? WHERE id=?").use{p->p.setLong(1,System.currentTimeMillis());p.setString(2,sessionId);p.executeUpdate()}}

    fun isMember(cid:String,uid:String)=query{c->c.prepareStatement("SELECT 1 FROM conversation_members WHERE conversation_id=? AND user_id=?").use{p->p.setString(1,cid);p.setString(2,uid);p.executeQuery().use{it.next()}}}

    fun conversations(uid: String): List<ConversationDto> = query { c ->
        c.prepareStatement("""
            SELECT c.id,c.type,c.title,c.created_at,
                   lm.id,lm.conversation_id,lm.sender_id,lm.body,lm.type,lm.created_at,
                   u.id,u.username,u.display_name,u.avatar_url,
                   COALESCE((SELECT COUNT(*) FROM messages um WHERE um.conversation_id=c.id AND um.created_at>COALESCE((SELECT cr.last_read_at FROM conversation_reads cr WHERE cr.conversation_id=c.id AND cr.user_id=?),0) AND um.sender_id<>? AND um.deleted_at IS NULL),0)
            FROM conversations c
            JOIN conversation_members cm ON cm.conversation_id=c.id AND cm.user_id=?
            LEFT JOIN LATERAL (SELECT * FROM messages m WHERE m.conversation_id=c.id AND m.deleted_at IS NULL ORDER BY m.created_at DESC LIMIT 1) lm ON true
            LEFT JOIN conversation_members om ON om.conversation_id=c.id AND om.user_id<>?
            LEFT JOIN users u ON u.id=om.user_id
            ORDER BY COALESCE(lm.created_at,c.created_at) DESC
        """.trimIndent()).use { p ->
            p.setString(1,uid);p.setString(2,uid);p.setString(3,uid);p.setString(4,uid)
            p.executeQuery().use { r -> buildList {
                while(r.next()) {
                    val last = if (r.getString(5) == null) null else MessageDto(r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getLong(10))
                    val other = if (r.getString(11) == null) null else UserDto(r.getString(11),r.getString(12),r.getString(13),r.getString(14))
                    add(ConversationDto(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),last,r.getInt(15),other))
                }
            } }
        }
    }

    fun createDirectConversation(uid: String, otherUid: String): ConversationDto = query { c ->
        require(uid != otherUid) { "Cannot create a conversation with yourself" }
        if (user(otherUid) == null) throw IllegalArgumentException("User not found")
        c.prepareStatement("""
            SELECT c.id,c.type,c.title,c.created_at FROM conversations c
            JOIN conversation_members a ON a.conversation_id=c.id AND a.user_id=?
            JOIN conversation_members b ON b.conversation_id=c.id AND b.user_id=?
            WHERE c.type='direct'
            LIMIT 1
        """.trimIndent()).use { p ->
            p.setString(1,uid);p.setString(2,otherUid);p.executeQuery().use { r ->
                if (r.next()) return@query ConversationDto(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),otherUser=user(otherUid)?.dto())
            }
        }
        val id=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        c.prepareStatement("INSERT INTO conversations(id,type,created_at) VALUES(?,?,?)").use{p->p.setString(1,id);p.setString(2,"direct");p.setLong(3,now);p.executeUpdate()}
        c.prepareStatement("INSERT INTO conversation_members(conversation_id,user_id,joined_at) VALUES(?,?,?),(?,?,?)").use{p->p.setString(1,id);p.setString(2,uid);p.setLong(3,now);p.setString(4,id);p.setString(5,otherUid);p.setLong(6,now);p.executeUpdate()}
        ConversationDto(id,"direct",null,now,otherUser=user(otherUid)?.dto())
    }

    fun markRead(cid:String,uid:String,at:Long=System.currentTimeMillis())=query{c->c.prepareStatement("INSERT INTO conversation_reads(conversation_id,user_id,last_read_at) VALUES(?,?,?) ON CONFLICT(conversation_id,user_id) DO UPDATE SET last_read_at=EXCLUDED.last_read_at").use{p->p.setString(1,cid);p.setString(2,uid);p.setLong(3,at);p.executeUpdate()}}

    fun messages(cid:String):List<MessageDto>=query{c->c.prepareStatement("SELECT id,conversation_id,sender_id,body,type,created_at FROM messages WHERE conversation_id=? AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 200").use{p->p.setString(1,cid);p.executeQuery().use{r->buildList{while(r.next())add(MessageDto(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6)))}}}}
    fun insertMessage(cid:String,uid:String,body:String,type:String)=MessageDto(UUID.randomUUID().toString(),cid,uid,body,type,System.currentTimeMillis()).also{m->query{c->c.prepareStatement("INSERT INTO messages(id,conversation_id,sender_id,body,type,created_at) VALUES(?,?,?,?,?,?)").use{p->p.setString(1,m.id);p.setString(2,cid);p.setString(3,uid);p.setString(4,body);p.setString(5,type);p.setLong(6,m.createdAt);p.executeUpdate()}}}
}

fun newPassword(password:String):Pair<String,String>{ val salt=ByteArray(16).also(SecureRandom()::nextBytes); val spec=PBEKeySpec(password.toCharArray(),salt,210000,256); val hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded; return Base64.getEncoder().encodeToString(hash) to Base64.getEncoder().encodeToString(salt) }
fun checkPassword(password:String,salt:String,expected:String):Boolean{ val spec=PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),210000,256); val hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded; return MessageDigest.isEqual(hash,Base64.getDecoder().decode(expected)) }
private fun sha256(v:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(v.toByteArray()))
