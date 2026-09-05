package amir.sepehr.seamchat.chat

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationPreview(
    val id: String,
    val title: String,
    val avatarLetter: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val online: Boolean = false
)

sealed interface ConversationListState {
    data object Loading : ConversationListState
    data class Ready(val conversations: List<ConversationPreview>) : ConversationListState
    data class Error(val message: String) : ConversationListState
}
