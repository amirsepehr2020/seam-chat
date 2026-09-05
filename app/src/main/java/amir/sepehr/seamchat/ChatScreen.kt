package amir.sepehr.seamchat

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import amir.sepehr.seamchat.chat.ChatViewModel
import amir.sepehr.seamchat.chat.MessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF07090E)
private val Glass = Color(0xFF12151D)
private val Glass2 = Color(0xFF191D27)
private val Cyan = Color(0xFF67E8F9)
private val Violet = Color(0xFF8B5CF6)

@Composable
fun SeamChatConversationScreen(
    conversationId: String = "demo",
    currentUserId: String? = null,
    onBack: () -> Unit = {}
) {
    val vm: ChatViewModel = viewModel()
    val messages by vm.messages.collectAsState()
    val connected by vm.connected.collectAsState()
    val typing by vm.typing.collectAsState()
    val error by vm.error.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val recording by vm.recording.collectAsState()
    val recordingSeconds by vm.recordingSeconds.collectAsState()
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageDto?>(null) }
    var selectedMedia by remember { mutableStateOf<MessageDto?>(null) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendMedia(conversationId, it, mediaType(context, it)) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoiceRecording()
    }

    LaunchedEffect(conversationId) { vm.load(conversationId) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        messages.lastOrNull()?.let { if (it.senderId != currentUserId) vm.markRead(it.id) }
    }

    if (selectedMedia != null) {
        MediaViewer(selectedMedia!!, onDismiss = { selectedMedia = null })
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Surface(color = Glass.copy(.96f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Avatar("A")
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Amir", color = Color.White, fontSize = 16.sp)
                    Text(
                        when { typing -> "typing…"; connected -> "online now"; else -> "connecting…" },
                        color = if (connected || typing) Cyan else Color.White.copy(.45f), fontSize = 11.sp
                    )
                }
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
            }
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(16.dp, 6.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(14.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item { Text("TODAY", color = Color.White.copy(.32f), fontSize = 10.sp) }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        mine = message.senderId == currentUserId,
                        onMedia = { selectedMedia = message },
                        onReply = { replyingTo = message },
                        onDelete = { vm.delete(message.id) },
                        onEdit = { vm.edit(message.id, it) },
                        onReact = { vm.react(message.id, it) }
                    )
                }
            }
        }

        if (replyingTo != null) {
            ReplyPreview(replyingTo!!) { replyingTo = null }
        }

        Surface(color = Glass.copy(.98f), modifier = Modifier.fillMaxWidth()) {
            Column {
                if (recording) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                        Spacer(Modifier.width(8.dp))
                        Text("Recording ${recordingSeconds}s", color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.cancelVoiceRecording() }) { Text("Cancel") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!recording) {
                        IconButton(enabled = !uploading, onClick = { picker.launch("*/*") }) {
                            Icon(Icons.Default.Add, "Attachment", tint = Cyan)
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                    if (recording) {
                        Text("Tap stop to send", color = Color.White.copy(.55f), modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.stopVoiceRecording(conversationId) }) {
                            Icon(Icons.Default.Stop, "Stop and send", tint = Color.Red)
                        }
                    } else {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it; vm.setTyping(it.isNotBlank()) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (uploading) "Uploading…" else "Message…") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        if (uploading) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Cyan)
                        } else if (draft.isBlank()) {
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    vm.startVoiceRecording()
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }) { Icon(Icons.Default.Mic, "Record voice", tint = Cyan) }
                        } else {
                            IconButton(onClick = {
                                val target = replyingTo
                                vm.setTyping(false)
                                if (target != null) vm.reply(target.id, draft) else vm.send(conversationId, draft)
                                replyingTo = null
                                draft = ""
                            }) { Icon(Icons.Default.Send, "Send", tint = Cyan) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(letter: String) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Cyan))),
        contentAlignment = Alignment.Center
    ) { Text(letter, color = Color.White, fontSize = 18.sp) }
}

@Composable
private fun MessageBubble(
    message: MessageDto,
    mine: Boolean,
    onMedia: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (String) -> Unit,
    onReact: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember(message.body) { mutableStateOf(message.body.orEmpty()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) Color(0xFF203A43) else Glass2,
            shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            modifier = Modifier.fillMaxWidth(.82f).combinedClickable(
                onClick = { if (message.type == "image" || message.type == "video") onMedia() },
                onLongClick = { menu = true }
            )
        ) {
            Column(Modifier.padding(10.dp)) {
                if (message.replyToMessageId != null) Text("↩ Reply", color = Cyan, fontSize = 10.sp)
                when (message.type) {
                    "image" -> MediaTile(Icons.Default.Image, "Photo")
                    "video" -> MediaTile(Icons.Default.VideoLibrary, "Video")
                    "audio" -> VoiceMessageBubble(message.body.orEmpty())
                    "file" -> MediaTile(Icons.Default.InsertDriveFile, "File")
                    else -> if (editing) {
                        TextField(value = editText, onValueChange = { editText = it }, singleLine = true)
                        Row {
                            TextButton(onClick = { onEdit(editText); editing = false }) { Text("Save") }
                            TextButton(onClick = { editing = false }) { Text("Cancel") }
                        }
                    } else {
                        Text(if (message.deleted) "Message deleted" else message.body.orEmpty(), color = Color.White, fontSize = 14.sp)
                    }
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)), color = Color.White.copy(.35f), fontSize = 9.sp)
                    if (message.editedAt != null) Text(" · edited", color = Color.White.copy(.3f), fontSize = 9.sp)
                }
            }
        }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Message") },
            text = {
                Column {
                    TextButton(onClick = { menu = false; onReply() }) { Text("Reply") }
                    TextButton(onClick = { menu = false; onReact("❤️") }) { Text("❤️ React") }
                    TextButton(onClick = { menu = false; onReact("👍") }) { Text("👍 React") }
                    if (mine) {
                        TextButton(onClick = { menu = false; editing = true }) { Text("Edit") }
                        TextButton(onClick = { menu = false; onDelete() }) { Text("Delete") }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun MediaTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.22f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = Cyan, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column { Text(label, color = Color.White); Text("Tap to preview", color = Color.White.copy(.45f), fontSize = 11.sp) }
    }
}

@Composable
private fun ReplyPreview(message: MessageDto, onClear: () -> Unit) {
    Surface(color = Glass2, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Reply, "Reply", tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Replying to", color = Cyan, fontSize = 10.sp)
                Text(message.body.orEmpty().ifBlank { "Media message" }, color = Color.White, maxLines = 1, fontSize = 12.sp)
            }
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Cancel", tint = Color.White) }
        }
    }
}

@Composable
private fun MediaViewer(message: MessageDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (message.type == "image") {
                    RemoteImage(message.body.orEmpty(), Modifier.fillMaxSize())
                } else {
                    Text("Video preview", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
                Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    IconButton(onClick = { downloadMedia(context, message.body.orEmpty()) }) { Icon(Icons.Default.Download, "Download", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun RemoteImage(url: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeStream(URL(url).openStream()) }.getOrNull() }
    }
    if (bitmap != null) {
        AndroidView(
            factory = { android.widget.ImageView(it) },
            update = { it.setImageBitmap(bitmap); it.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER },
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
    }
}

private fun downloadMedia(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("SEAM Chat media")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SEAM_${System.currentTimeMillis()}")
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}

private fun mediaType(context: Context, uri: Uri): String = when (context.contentResolver.getType(uri)?.substringBefore('/')) {
    "image" -> "image"
    "video" -> "video"
    "audio" -> "audio"
    else -> "file"
}
