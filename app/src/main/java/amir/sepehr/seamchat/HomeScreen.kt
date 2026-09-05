package amir.sepehr.seamchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import amir.sepehr.seamchat.auth.AuthRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

private val Bg = Color(0xFF07090E)
private val Glass = Color(0xFF121722)
private val Cyan = Color(0xFF67E8F9)
private val Violet = Color(0xFF8B5CF6)

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF11182E), Bg))), contentAlignment = Alignment.Center) {
        Text("SEAM CHAT", color = Color.White, fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun SeamChatHome(onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { AuthRepository(context) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SEAM CHAT", color = Color.White, fontSize = 27.sp)
                Text("Your private space", color = Cyan.copy(.8f), fontSize = 12.sp)
            }
            IconButton(onClick = { kotlinx.coroutines.MainScope().launch { repository.logout(); onLogout() } }) { Icon(Icons.Default.Logout, "Log out", tint = Color.White) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(color = Glass, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(Brush.linearGradient(listOf(Violet,Cyan)), MaterialTheme.shapes.large), contentAlignment = Alignment.Center) { Text("A", color = Color.White, fontSize = 22.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text("Amir", color = Color.White, fontSize = 16.sp); Text("Open conversation", color = Color.White.copy(.45f), fontSize = 12.sp) }
                        IconButton(onClick = {}) { Icon(Icons.Default.Add, "Open", tint = Cyan) }
                    }
                }
            }
            item {
                Surface(color = Glass.copy(.75f), shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Ready for realtime chat", color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Authentication, sessions, PostgreSQL persistence and WebSocket transport are connected to the self-hosted backend.", color = Color.White.copy(.5f), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}
