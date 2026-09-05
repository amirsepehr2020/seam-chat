package amir.sepehr.seamchat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import amir.sepehr.seamchat.chat.ChatViewModel
import amir.sepehr.seamchat.chat.MessageDto

private val Bg=Color(0xFF07090E)
private val Glass=Color(0xFF12151D)
private val Glass2=Color(0xFF191D27)
private val Cyan=Color(0xFF67E8F9)
private val Violet=Color(0xFF8B5CF6)

@Composable
fun SeamChatConversationScreen(conversationId:String="demo",currentUserId:String?=null,onBack:()->Unit={}) {
 val vm:ChatViewModel=viewModel();val messages by vm.messages.collectAsState();val connected by vm.connected.collectAsState();val typing by vm.typing.collectAsState();val error by vm.error.collectAsState();val uploading by vm.uploading.collectAsState();var draft by remember{mutableStateOf("")}
 val context=androidx.compose.ui.platform.LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri? -> uri?.let{vm.sendMedia(conversationId,it,mediaType(context,it))}}
 LaunchedEffect(conversationId){vm.load(conversationId)}
 LaunchedEffect(messages.lastOrNull()?.id){messages.lastOrNull()?.let{if(it.senderId!=currentUserId)vm.markRead(it.id)}}
 Column(Modifier.fillMaxSize().background(Bg)){
  Surface(color=Glass.copy(.96f),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(8.dp,10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)};Avatar("A",46);Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text("Amir",color=Color.White,fontSize=16.sp);Text(when{typing->"typing…";connected->"online now";else->"connecting…"},color=if(connected||typing)Cyan else Color.White.copy(.45f),fontSize=11.sp)};IconButton(onClick={}){Icon(Icons.Default.Call,"Call",tint=Color.White)};IconButton(onClick={}){Icon(Icons.Default.MoreVert,"More",tint=Color.White)}}}
  if(error!=null)Text(error?:"",color=MaterialTheme.colorScheme.error,fontSize=11.sp,modifier=Modifier.padding(16.dp,6.dp))
  Box(Modifier.weight(1f).fillMaxWidth()){LazyColumn(Modifier.fillMaxSize().padding(14.dp,16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Text("TODAY",color=Color.White.copy(.32f),fontSize=10.sp)};items(messages,key={it.id}){MessageBubble(it,it.senderId==currentUserId)}}}
  Surface(color=Glass.copy(.98f),shape=RoundedCornerShape(26.dp,26.dp,0.dp,0.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(enabled=!uploading,onClick={picker.launch("*/*")}){Icon(Icons.Default.Add,"Attachment",tint=Cyan)};TextField(value=draft,onValueChange={draft=it;vm.setTyping(it.isNotBlank())},modifier=Modifier.weight(1f),placeholder={Text(if(uploading)"Uploading…" else "Message…",color=Color.White.copy(.35f))},singleLine=true,shape=RoundedCornerShape(24.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Glass2,unfocusedContainerColor=Glass2,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedTextColor=Color.White,unfocusedTextColor=Color.White,cursorColor=Cyan));Spacer(Modifier.width(6.dp));if(uploading){CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp,color=Cyan)}else Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet,Cyan))),contentAlignment=Alignment.Center){IconButton(enabled=draft.isNotBlank(),onClick={vm.setTyping(false);vm.send(conversationId,draft);draft=""}){Icon(Icons.Default.Send,"Send",tint=Color.White)}}}}
 }
}

@Composable private fun MessageBubble(message:MessageDto,mine:Boolean){Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Surface(color=if(mine)Color(0xFF203A43)else Glass2,shape=if(mine)RoundedCornerShape(20.dp,20.dp,6.dp,20.dp)else RoundedCornerShape(20.dp,20.dp,20.dp,6.dp),modifier=Modifier.fillMaxWidth(.82f)){Row(Modifier.padding(14.dp,10.dp),verticalAlignment=Alignment.Bottom){when(message.type){"image"->Icon(Icons.Default.Image,"Image",tint=Cyan);"video"->Icon(Icons.Default.VideoLibrary,"Video",tint=Cyan);"audio"->Icon(Icons.Default.AudioFile,"Audio",tint=Cyan);"file"->Icon(Icons.Default.InsertDriveFile,"File",tint=Cyan);else->Text(message.body.orEmpty(),color=Color.White,fontSize=14.sp,modifier=Modifier.weight(1f,false))};Spacer(Modifier.width(8.dp));Text(java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(message.createdAt)),color=Color.White.copy(.35f),fontSize=9.sp)}}}}
@Composable private fun Avatar(letter:String,size:Int){Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet,Cyan))),contentAlignment=Alignment.Center){Text(letter,color=Color.White,fontSize=(size/2.5).sp)}}

private fun mediaType(context:Context,uri:Uri):String=when(context.contentResolver.getType(uri)?.substringBefore('/')){"image"->"image";"video"->"video";"audio"->"audio";else->"file"}
