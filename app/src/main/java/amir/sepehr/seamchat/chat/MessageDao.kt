package amir.sepehr.seamchat.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)
}
