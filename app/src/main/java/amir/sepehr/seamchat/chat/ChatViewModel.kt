package amir.sepehr.seamchat.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(conversationId: String) {
        viewModelScope.launch {
            repository.loadMessages(conversationId)
                .onSuccess { _messages.value = it; _error.value = null }
                .onFailure { _error.value = it.message }
        }
    }

    fun send(conversationId: String, body: String) {
        if (body.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            repository.sendMessage(conversationId, body.trim())
                .onSuccess { _messages.value = _messages.value + it; _error.value = null }
                .onFailure { _error.value = it.message }
            _sending.value = false
        }
    }
}
