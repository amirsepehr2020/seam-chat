package amir.sepehr.seamchat.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Conversation boundary. Replace the local source with the production API once the endpoint is exposed. */
class ConversationRepository {
    fun observeConversations(): Flow<List<ConversationPreview>> = flow {
        emit(
            listOf(
                ConversationPreview("demo-1", "SEAM CHAT", "S", "Realtime is ready 🚀", "Now", 2, true),
                ConversationPreview("demo-2", "Amir", "A", "See you soon", "14:32", 0, true)
            )
        )
    }
}
