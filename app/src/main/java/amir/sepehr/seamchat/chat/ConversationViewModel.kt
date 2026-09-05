package amir.sepehr.seamchat.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(private val repository: ConversationRepository) : ViewModel() {
    private val _state = MutableStateFlow<ConversationListState>(ConversationListState.Loading)
    val state: StateFlow<ConversationListState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ConversationListState.Loading
            repository.observeConversations().collect { result ->
                _state.value = result.fold(
                    onSuccess = { conversations ->
                        ConversationListState.Ready(
                            conversations.map {
                                ConversationPreview(
                                    id = it.id,
                                    title = it.title ?: it.otherUser?.displayName ?: it.otherUser?.username ?: "Conversation",
                                    avatarLetter = (it.otherUser?.displayName ?: it.otherUser?.username ?: it.title ?: "S").firstOrNull()?.uppercase() ?: "S",
                                    lastMessage = "",
                                    timestamp = "",
                                    online = false
                                )
                            }
                        )
                    },
                    onFailure = { ConversationListState.Error(it.message ?: "Unable to load conversations") }
                )
            }
        }
    }
}
