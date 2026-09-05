package amir.sepehr.seamchat.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "createdAt"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String?,
    val type: String,
    val createdAt: Long
)

fun MessageEntity.toDto() = MessageDto(id, conversationId, senderId, body, type, createdAt)
fun MessageDto.toEntity() = MessageEntity(id, conversationId, senderId, body, type, createdAt)
