package amir.sepehr.seamchat.chat

import amir.sepehr.seamchat.ConversationDto
import amir.sepehr.seamchat.UserDto
import amir.sepehr.seamchat.network.ConversationApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ConversationRepository(private val api: ConversationApi) {
    fun observeConversations(): Flow<Result<List<ConversationDto>>> = flow {
        emit(runCatching { api.conversations() })
    }

    suspend fun searchUsers(query: String): Result<List<UserDto>> = runCatching {
        if (query.trim().length < 2) emptyList() else api.searchUsers(query.trim())
    }

    suspend fun openDirectChat(userId: String): Result<ConversationDto> = runCatching {
        api.createDirect(amir.sepehr.seamchat.CreateDirectConversationRequest(userId))
    }
}
