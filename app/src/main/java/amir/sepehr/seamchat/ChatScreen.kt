package amir.sepehr.seamchat

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
    val error by vm.error.collectAsState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(conversationId) { vm.load(conversationId) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Surface(color = Glass.copy(alpha = .96f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Avatar("A", 46)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Amir", color = Color.White, fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                    Text(if (connected) "online now" else "connecting…", color = if (connected) Cyan else Color.White.copy(.45f), fontSize = 11.sp)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Call, "Call", tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.Videocam, "Video call", tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
            }
        }
        if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { Text("TODAY", color = Color.White.copy(.32f), fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)) }
                items(messages, key = { it.id }) { MessageBubble(it, it.senderId == currentUserId) }
            }
        }
        Surface(color = Glass.copy(alpha = .98f), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.Add, "Attachment", tint = Cyan) }
                TextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message…", color = Color.White.copy(.35f)) }, singleLine = true, shape = RoundedCornerShape(24.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Glass2, unfocusedContainerColor = Glass2, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Cyan))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Cyan))), contentAlignment = Alignment.Center) {
                    IconButton(enabled = draft.isNotBlank(), onClick = { vm.send(conversationId, draft); draft = "" }) { Icon(Icons.Default.Send, "Send", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageDto, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = if (mine) Color(0xFF203A43) else Glass2, shape = if (mine) RoundedCornerShape(20.dp,20.dp,6.dp,20.dp) else RoundedCornerShape(20.dp,20.dp,20.dp,6.dp), modifier = Modifier.fillMaxWidth(.82f)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Text(message.body.orEmpty(), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f, false))
                Spacer(Modifier.width(8.dp))
                Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.createdAt)), color = Color.White.copy(.35f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun Avatar(letter: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Cyan))), contentAlignment = Alignment.Center) {
        Text(letter, color = Color.White, fontSize = (size / 2.5).sp, style = MaterialTheme.typography.titleMedium)
    }
}
