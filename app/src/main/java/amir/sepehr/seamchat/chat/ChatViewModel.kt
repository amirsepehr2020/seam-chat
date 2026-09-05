package amir.sepehr.seamchat.chat
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import amir.sepehr.seamchat.notifications.SeamNotificationCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class ChatViewModel(application:Application):AndroidViewModel(application){
 private val repository=ChatRepository(application);private val socket=ChatWebSocket(application);private val notifications=SeamNotificationCoordinator(application);private val voice=VoiceMessageController(application);private var socketJob:Job?=null;private var cacheJob:Job?=null;private var lastMedia:Triple<String,Uri,String>?=null
 private val _messages=MutableStateFlow<List<MessageDto>>(emptyList());val messages:StateFlow<List<MessageDto>>=_messages.asStateFlow();private val _sending=MutableStateFlow(false);val sending:StateFlow<Boolean>=_sending.asStateFlow();private val _uploading=MutableStateFlow(false);val uploading:StateFlow<Boolean>=_uploading.asStateFlow();private val _uploadError=MutableStateFlow<String?>(null);val uploadError:StateFlow<String?>=_uploadError.asStateFlow();private val _connected=MutableStateFlow(false);val connected:StateFlow<Boolean>=_connected.asStateFlow();private val _typing=MutableStateFlow(false);val typing:StateFlow<Boolean>=_typing.asStateFlow();private val _onlineUsers=MutableStateFlow<Set<String>>(emptySet());val onlineUsers:StateFlow<Set<String>>=_onlineUsers.asStateFlow();private val _readMessageIds=MutableStateFlow<Set<String>>(emptySet());val readMessageIds:StateFlow<Set<String>>=_readMessageIds.asStateFlow();private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?>=_error.asStateFlow();private val _recording=MutableStateFlow(false);val recording:StateFlow<Boolean>=_recording.asStateFlow();private val _recordingSeconds=MutableStateFlow(0);val recordingSeconds:StateFlow<Int>=_recordingSeconds.asStateFlow()
 fun load(id:String){cacheJob?.cancel();cacheJob=viewModelScope.launch{repository.observeCachedMessages(id).collect{_messages.value=it.distinctBy(MessageDto::id).sortedBy(MessageDto::createdAt)}};viewModelScope.launch{repository.refreshMessages(id).onFailure{_error.value=it.message}};connectRealtime(id)}
 private fun connectRealtime(id:String){socketJob?.cancel();socketJob=viewModelScope.launch{socket.connect(id).collect{event->when(event){is SocketEvent.Connected->_connected.value=event.value;is SocketEvent.Message->{repository.cache(event.value);notifications.showMessage(id.hashCode().toLong(),"SEAM Chat",event.value.body.orEmpty().ifBlank{"New message"})};is SocketEvent.Typing->_typing.value=event.value;is SocketEvent.Presence->_onlineUsers.value=if(event.online)_onlineUsers.value+event.userId else _onlineUsers.value-event.userId;is SocketEvent.Read->_readMessageIds.value=_readMessageIds.value+event.messageId;is SocketEvent.Failed->_error.value=event.error.message}}}}
 fun send(id:String,body:String,type:String="text"){if(body.isBlank()||_sending.value)return;viewModelScope.launch{_sending.value=true;repository.sendMessage(id,body.trim(),type).onFailure{_error.value=it.message};_sending.value=false}}
 fun reply(messageId:String,body:String,type:String="text"){if(body.isBlank()||_sending.value)return;viewModelScope.launch{_sending.value=true;repository.reply(messageId,body.trim(),type).onFailure{_error.value=it.message};_sending.value=false}}
 fun sendMedia(id:String,uri:Uri,type:String){if(_uploading.value)return;lastMedia=Triple(id,uri,type);upload(id,uri,type)}
 fun retryMedia(){lastMedia?.let{upload(it.first,it.second,it.third)}}
 private fun upload(id:String,uri:Uri,type:String){viewModelScope.launch{_uploadError.value=null;_uploading.value=true;repository.uploadMedia(uri,type).onSuccess{send(id,it.url,type)}.onFailure{_uploadError.value=it.message};_uploading.value=false}}
 fun startVoiceRecording():Boolean{if(_recording.value||_uploading.value)return false;val ok=voice.start();if(ok){_recording.value=true;_recordingSeconds.value=0;viewModelScope.launch{while(_recording.value){kotlinx.coroutines.delay(1000);if(_recording.value)_recordingSeconds.value++}}};return ok}
 fun stopVoiceRecording(id:String){if(!_recording.value)return;val uri=voice.stop();_recording.value=false;_recordingSeconds.value=0;uri?.let{sendMedia(id,it,"audio")}}
 fun cancelVoiceRecording(){if(!_recording.value)return;voice.cancel();_recording.value=false;_recordingSeconds.value=0}
 fun edit(messageId:String,body:String){viewModelScope.launch{repository.editMessage(messageId,body).onFailure{_error.value=it.message}}}
 fun delete(messageId:String){viewModelScope.launch{repository.deleteMessage(messageId).onSuccess{_messages.value=_messages.value.filterNot{it.id==messageId}}.onFailure{_error.value=it.message}}}
 fun forward(messageId:String,targetConversationId:String){viewModelScope.launch{repository.forwardMessage(messageId,targetConversationId).onFailure{_error.value=it.message}}}
 fun react(messageId:String,reaction:String){viewModelScope.launch{repository.react(messageId,reaction).onFailure{_error.value=it.message}}}
 fun unreact(messageId:String,reaction:String){viewModelScope.launch{repository.unreact(messageId,reaction).onFailure{_error.value=it.message}}}
 fun setTyping(value:Boolean)=socket.setTyping(value);fun markRead(messageId:String)=socket.markRead(messageId);override fun onCleared(){voice.cancel();cacheJob?.cancel();socketJob?.cancel();socket.close();super.onCleared()}
}
