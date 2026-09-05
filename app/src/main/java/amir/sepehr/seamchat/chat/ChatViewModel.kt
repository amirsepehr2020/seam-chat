package amir.sepehr.seamchat.chat

import android.app.Application
import android.net.Uri
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
    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()
    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()
    private val _readMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val readMessageIds: StateFlow<Set<String>> = _readMessageIds.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(conversationId: String) {
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            repository.observeCachedMessages(conversationId).collect { cached ->
                _messages.value = cached.distinctBy(MessageDto::id).sortedBy(MessageDto::createdAt)
            }
        }
        viewModelScope.launch { repository.refreshMessages(conversationId).onFailure { _error.value = it.message } }
        connectRealtime(conversationId)
    }

    private fun connectRealtime(conversationId: String) {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socket.connect(conversationId).collect { event ->
                when (event) {
                    is SocketEvent.Connected -> _connected.value = event.value
                    is SocketEvent.Message -> { repository.cache(event.value); _error.value = null }
                    is SocketEvent.Typing -> _typing.value = event.value
                    is SocketEvent.Presence -> _onlineUsers.value = if (event.online) _onlineUsers.value + event.userId else _onlineUsers.value - event.userId
                    is SocketEvent.Read -> _readMessageIds.value = _readMessageIds.value + event.messageId
                    is SocketEvent.Failed -> _error.value = event.error.message
                }
            }
        }
    }

    fun send(conversationId: String, body: String, type: String = "text") {
        if (body.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            repository.sendMessage(conversationId, body.trim(), type)
                .onFailure { _error.value = it.message }
                .onSuccess { _error.value = null }
            _sending.value = false
        }
    }

    fun sendMedia(conversationId: String, uri: Uri, type: String) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            repository.uploadMedia(uri, type)
                .onSuccess { media -> send(conversationId, media.url, type) }
                .onFailure { _error.value = it.message }
            _uploading.value = false
        }
    }

    fun setTyping(value: Boolean) { socket.setTyping(value) }
    fun markRead(messageId: String) { socket.markRead(messageId) }

    override fun onCleared() { cacheJob?.cancel(); socketJob?.cancel(); socket.close(); super.onCleared() }
}
