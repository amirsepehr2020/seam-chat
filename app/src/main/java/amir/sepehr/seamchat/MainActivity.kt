package amir.sepehr.seamchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF07090E)
private val Card = Color(0xFF11151D)
private val Cyan = Color(0xFF67E8F9)
private val Violet = Color(0xFF8B5CF6)

data class Chat(val name: String, val preview: String, val time: String, val unread: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeamChatHome() }
    }
}

@Composable
fun SeamChatHome() {
    val chats = listOf(
        Chat("Amir", "داداش این UI رو ببین 🔥", "14:32", 3),
        Chat("Sepideh", "فردا میای؟", "13:08", 0),
        Chat("SEAM Team", "New message arrived", "دیروز", 0)
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF11182E), Bg)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("SEAM CHAT", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your private space", color = Color.White.copy(.48f), fontSize = 13.sp)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.Settings, null, tint = Color.White) }
            }
            Spacer(Modifier.height(20.dp))
            Surface(RoundedCornerShape(26.dp), color = Card, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar("S", 56)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("سپهر", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Online now", color = Cyan, fontSize = 12.sp)
                    }
                    IconButton(onClick = {}) { Icon(Icons.Default.Add, null, tint = Cyan) }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Messages", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(chats) { chat -> ChatRow(chat) }
            }
        }
    }
}

@Composable
private fun Avatar(letter: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Cyan))), contentAlignment = Alignment.Center) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.7).sp)
    }
}

@Composable
private fun ChatRow(chat: Chat) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(chat.name.take(1), 52)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(chat.preview, color = Color.White.copy(.52f), fontSize = 13.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = Color.White.copy(.35f), fontSize = 11.sp)
            if (chat.unread > 0) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(21.dp).clip(CircleShape).background(Cyan), contentAlignment = Alignment.Center) {
                    Text(chat.unread.toString(), color = Bg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
