package amir.sepehr.seamchat

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import amir.sepehr.seamchat.chat.ChatViewModel
import amir.sepehr.seamchat.chat.MessageDto
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg=Color(0xFF07090E)
private val Glass=Color(0xFF12151D)
private val Glass2=Color(0xFF191D27)
private val Cyan=Color(0xFF67E8F9)
private val Violet=Color(0xFF8B5CF6)

@Composable
fun SeamChatConversationScreen(conversationId:String="demo",currentUserId:String?=null,onBack:()->Unit={}) {
 val vm:ChatViewModel=viewModel();val messages by vm.messages.collectAsState();val connected by vm.connected.collectAsState();val typing by vm.typing.collectAsState();val error by vm.error.collectAsState();val uploading by vm.uploading.collectAsState();var draft by remember{mutableStateOf("")};var selectedMedia by remember{mutableStateOf<MessageDto?>(null)};var replyingTo by remember{mutableStateOf<MessageDto?>(null)}
 val context=LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri? -> uri?.let{vm.sendMedia(conversationId,it,mediaType(context,it))}}
 LaunchedEffect(conversationId){vm.load(conversationId)}
 LaunchedEffect(messages.lastOrNull()?.id){messages.lastOrNull()?.let{if(it.senderId!=currentUserId)vm.markRead(it.id)}}
 if(selectedMedia!=null) MediaViewer(message=selectedMedia!!,onDismiss={selectedMedia=null})
 Column(Modifier.fillMaxSize().background(Bg)){
  Surface(color=Glass.copy(.96f),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(8.dp,10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)};Avatar("A",46);Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text("Amir",color=Color.White,fontSize=16.sp);Text(when{typing->"typing…";connected->"online now";else->"connecting…"},color=if(connected||typing)Cyan else Color.White.copy(.45f),fontSize=11.sp)};IconButton(onClick={}){Icon(Icons.Default.Call,"Call",tint=Color.White)};IconButton(onClick={}){Icon(Icons.Default.MoreVert,"More",tint=Color.White)}}}
  if(error!=null)Text(error?:"",color=MaterialTheme.colorScheme.error,fontSize=11.sp,modifier=Modifier.padding(16.dp,6.dp))
  Box(Modifier.weight(1f).fillMaxWidth()){LazyColumn(Modifier.fillMaxSize().padding(14.dp,16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Text("TODAY",color=Color.White.copy(.32f),fontSize=10.sp)};items(messages,key={it.id}){message->MessageBubble(message=message,mine=message.senderId==currentUserId,onMedia={selectedMedia=message},onReply={replyingTo=message},onDelete={vm.delete(message.id)},onEdit={vm.edit(message.id,it)},onReact={vm.react(message.id,it)},onUnreact={vm.unreact(message.id,it)})}}}
  if(replyingTo!=null) ReplyPreview(replyingTo!!){replyingTo=null}
  Surface(color=Glass.copy(.98f),shape=RoundedCornerShape(26.dp,26.dp,0.dp,0.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(enabled=!uploading,onClick={picker.launch("*/*")}){Icon(Icons.Default.Add,"Attachment",tint=Cyan)};TextField(value=draft,onValueChange={draft=it;vm.setTyping(it.isNotBlank())},modifier=Modifier.weight(1f),placeholder={Text(if(uploading)"Uploading…" else if(replyingTo!=null)"Reply…" else "Message…",color=Color.White.copy(.35f))},singleLine=true,shape=RoundedCornerShape(24.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Glass2,unfocusedContainerColor=Glass2,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedTextColor=Color.White,unfocusedTextColor=Color.White,cursorColor=Cyan));Spacer(Modifier.width(6.dp));if(uploading){CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp,color=Cyan)}else Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet,Cyan))),contentAlignment=Alignment.Center){IconButton(enabled=draft.isNotBlank(),onClick={vm.setTyping(false);val target=replyingTo;if(target!=null)vm.reply(target.id,draft)else vm.send(conversationId,draft);replyingTo=null;draft=""}){Icon(Icons.Default.Send,"Send",tint=Color.White)}}}}
 }
}

@Composable private fun MessageBubble(message:MessageDto,mine:Boolean,onMedia:(MessageDto)->Unit,onReply:()->Unit,onDelete:()->Unit,onEdit:(String)->Unit,onReact:(String)->Unit,onUnreact:(String)->Unit){
 var menu by remember{mutableStateOf(false)};var editing by remember{mutableStateOf(false)};var editText by remember(message.body){mutableStateOf(message.body.orEmpty())}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Box{Surface(color=if(mine)Color(0xFF203A43)else Glass2,shape=if(mine)RoundedCornerShape(20.dp,20.dp,6.dp,20.dp)else RoundedCornerShape(20.dp,20.dp,20.dp,6.dp),modifier=Modifier.fillMaxWidth(.82f).clickable(enabled=message.type=="image"||message.type=="video"){onMedia(message)}.combinedClickable(onLongClick={menu=true},onClick={})){
  Column(Modifier.padding(10.dp)){if(message.replyToMessageId!=null)Text("↩ Reply",color=Cyan,fontSize=10.sp);when(message.type){"image"->MediaTile(Icons.Default.Image,"Photo",Cyan);"video"->MediaTile(Icons.Default.VideoLibrary,"Video",Cyan);"audio"->MediaTile(Icons.Default.AudioFile,"Audio",Cyan);"file"->MediaTile(Icons.Default.InsertDriveFile,"File",Cyan);else->{if(editing){TextField(value=editText,onValueChange={editText=it},singleLine=true,modifier=Modifier.fillMaxWidth());Row{TextButton(onClick={onEdit(editText);editing=false}){Text("Save")};TextButton(onClick={onDelete){Text("Cancel")}}}else Text(message.body.orEmpty(),color=Color.White,fontSize=14.sp)}};Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.align(Alignment.End)){Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(message.createdAt)),color=Color.White.copy(.35f),fontSize=9.sp);if(message.editedAt!=null)Text(" · edited",color=Color.White.copy(.3f),fontSize=9.sp)}}}}
 if(menu){MessageActionsDialog(message,mine,{menu=false;onReply()},{menu=false;editing=true},{menu=false;onDelete()},{menu=false;onReact("❤️")},{menu=false;onReact("👍")})}
 }}
}

@Composable private fun MediaTile(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,tint:Color){Row(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.22f)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,label,tint=tint,modifier=Modifier.size(42.dp));Spacer(Modifier.width(12.dp));Column{Text(label,color=Color.White,fontSize=14.sp);Text("Tap to preview",color=Color.White.copy(.45f),fontSize=11.sp)}}}

@Composable private fun ReplyPreview(message:MessageDto,onClear:()->Unit){Surface(color=Glass2,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Reply,"Reply",tint=Cyan);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text("Replying to",color=Cyan,fontSize=10.sp);Text(message.body.orEmpty().ifBlank{"Media message"},color=Color.White,maxLines=1,fontSize=12.sp)};IconButton(onClick=onClear){Icon(Icons.Default.Close,"Cancel",tint=Color.White)}}}}

@Composable private fun MessageActionsDialog(message:MessageDto,mine:Boolean,onReply:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onHeart:()->Unit,onLike:()->Unit){AlertDialog(onDismissRequest=onReply,title={Text("Message")},text={Column{TextButton(onClick=onReply){Text("Reply")};TextButton(onClick=onLike){Text("👍 React")};TextButton(onClick=onHeart){Text("❤️ React")};if(mine){TextButton(onClick=onEdit){Text("Edit")};TextButton(onClick=onDelete){Text("Delete")}};TextButton(onClick={}){Text("Forward")}}},confirmButton={})}

@Composable private fun MediaViewer(message:MessageDto,onDismiss:()->Unit){val context=LocalContext.current;DialogLike(onDismiss){Surface(color=Color.Black,modifier=Modifier.fillMaxSize()){Box(Modifier.fillMaxSize()){when(message.type){"video"->AndroidView(factory={ctx->VideoView(ctx).apply{setVideoURI(Uri.parse(message.body));setMediaController(MediaController(ctx).also{it.setAnchorView(this)});setOnPreparedListener{it.isLooping=false;start()}}},modifier=Modifier.fillMaxSize());"image"->RemoteImage(url=message.body.orEmpty(),modifier=Modifier.fillMaxSize());else->Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.InsertDriveFile,"File",tint=Cyan,modifier=Modifier.size(72.dp));Text("Preview unavailable",color=Color.White)}};Row(Modifier.align(Alignment.TopEnd).padding(12.dp)){IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"Close",tint=Color.White)};IconButton(onClick={downloadMedia(context,message.body.orEmpty())}){Icon(Icons.Default.Download,"Download",tint=Color.White)}}}}}}

@Composable private fun RemoteImage(url:String,modifier:Modifier){var bitmap by remember(url){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(url){bitmap=withContext(Dispatchers.IO){runCatching{android.graphics.BitmapFactory.decodeStream(URL(url).openStream())}.getOrNull()}};if(bitmap!=null)AndroidView(factory={ctx->android.widget.ImageView(ctx)},update={it.setImageBitmap(bitmap);it.scaleType=android.widget.ImageView.ScaleType.FIT_CENTER},modifier=modifier)else Box(modifier,contentAlignment=Alignment.Center){CircularProgressIndicator(color=Cyan)}}

@Composable private fun DialogLike(onDismiss:()->Unit,content:@Composable()->Unit){androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){content()}}

private fun downloadMedia(context:Context,url:String){if(url.isBlank())return;runCatching{val request=DownloadManager.Request(Uri.parse(url)).setTitle("SEAM Chat media").setDescription("Downloading media").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"SEAM_${System.currentTimeMillis()}");(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)}}
private fun mediaType(context:Context,uri:Uri):String=when(context.contentResolver.getType(uri)?.substringBefore('/')){"image"->"image";"video"->"video";"audio"->"audio";else->"file"}
