package amir.sepehr.seamchat.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private val socket = ChatWebSocket(application)
    private var socketJob: Job? = null
    private var cacheJob: Job? = null

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(conversationId: String) {
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            repository.observeCachedMessages(conversationId).collect { cached ->
                _messages.value = cached.distinctBy(MessageDto::id).sortedBy(MessageDto::createdAt)
            }
        }
        viewModelScope.launch {
            repository.refreshMessages(conversationId)
                .onFailure { _error.value = it.message }
        }
        connectRealtime(conversationId)
    }

    private fun connectRealtime(conversationId: String) {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socket.connect(conversationId).collect { event ->
                when (event) {
                    is SocketEvent.Connected -> _connected.value = event.value
                    is SocketEvent.Message -> {
                        repository.cache(event.value)
                        _error.value = null
                    }
                    is SocketEvent.Failed -> _error.value = event.error.message
                }
            }
        }
    }

    fun send(conversationId: String, body: String) {
        if (body.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            repository.sendMessage(conversationId, body.trim())
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message }
            _sending.value = false
        }
    }

    override fun onCleared() {
        cacheJob?.cancel()
        socketJob?.cancel()
        socket.close()
        super.onCleared()
    }
}
