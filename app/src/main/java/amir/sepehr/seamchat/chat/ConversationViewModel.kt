package amir.sepehr.seamchat.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val repository: ConversationRepository = ConversationRepository()
) : ViewModel() {
    private val _state = MutableStateFlow<ConversationListState>(ConversationListState.Loading)
    val state: StateFlow<ConversationListState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ConversationListState.Loading
            runCatching { repository.observeConversations().collect { _state.value = ConversationListState.Ready(it) } }
                .onFailure { _state.value = ConversationListState.Error(it.message ?: "Unable to load conversations") }
        }
    }
}
