package amir.sepehr.seamchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF07090E)
private val Glass = Color(0xFF12151D)
private val Glass2 = Color(0xFF191D27)
private val Cyan = Color(0xFF67E8F9)
private val Violet = Color(0xFF8B5CF6)

private data class DemoMessage(val text: String, val mine: Boolean, val time: String)

@Composable
fun SeamChatConversationScreen() {
    var draft by remember { mutableStateOf("") }
    val messages = remember {
        listOf(
            DemoMessage("داداش این UI رو دیدی؟ 🔥", false, "14:28"),
            DemoMessage("آره، خیلی خفن شده 😭", true, "14:29"),
            DemoMessage("چت رو هم باید Liquid Glass کنیم.", false, "14:30"),
            DemoMessage("صددرصد. SEAM باید یه چیز متفاوت باشه.", true, "14:31"),
            DemoMessage("پس بزن بریم 🚀", false, "14:32")
        )
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Surface(color = Glass.copy(alpha = .96f), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Avatar("A", 46)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Amir", color = Color.White, fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                    Text("online now", color = Cyan, fontSize = 11.sp)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Call, "Call", tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.Videocam, "Video call", tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item { Text("TODAY", color = Color.White.copy(alpha = .32f), fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)) }
                items(messages) { MessageBubble(it) }
            }
        }

        Surface(
            color = Glass.copy(alpha = .98f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Add, "Attachment", tint = Cyan)
                }
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…", color = Color.White.copy(alpha = .35f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Glass2,
                        unfocusedContainerColor = Glass2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Cyan
                    )
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Violet, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { draft = "" }) {
                        Icon(Icons.Default.Send, "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: DemoMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.mine) Color(0xFF203A43) else Glass2,
            shape = if (message.mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
            else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            modifier = Modifier.fillMaxWidth(.82f)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Text(message.text, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f, false))
                Spacer(Modifier.width(8.dp))
                Text(message.time, color = Color.White.copy(alpha = .35f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun Avatar(letter: String, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Cyan))),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontSize = (size / 2.5).sp, style = MaterialTheme.typography.titleMedium)
    }
}
