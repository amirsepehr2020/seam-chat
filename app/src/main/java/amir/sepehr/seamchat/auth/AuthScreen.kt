package amir.sepehr.seamchat.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Bg = Color(0xFF07090E)
private val Card = Color(0xFF121722)
private val Cyan = Color(0xFF67E8F9)
private val Violet = Color(0xFF8B5CF6)

@Composable
fun SeamAuthScreen(onAuthenticated: () -> Unit = {}) {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { AuthRepository(LocalContext.current) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF11182E), Bg))), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Violet, Cyan))), contentAlignment = Alignment.Center) {
                Text("S", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(18.dp))
            Text("SEAM CHAT", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (register) "Create your private account" else "Welcome back to your private space", color = Color.White.copy(.5f), fontSize = 13.sp)
            Spacer(Modifier.height(26.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(username, { username = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") }, shape = RoundedCornerShape(18.dp))
                OutlinedTextField(password, { password = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(18.dp))
                if (register) OutlinedTextField(inviteCode, { inviteCode = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Invite code") }, shape = RoundedCornerShape(18.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true; error = null
                        scope.launch {
                            val result = if (register) repository.register(username, password, inviteCode) else repository.login(username, password)
                            loading = false
                            result.onSuccess { onAuthenticated() }.onFailure { error = it.message ?: "Could not connect to SEAM CHAT." }
                        }
                    },
                    Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) { if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White) else Text(if (register) "Create account" else "Sign in", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (register) "Already have an account?" else "New to SEAM CHAT?", color = Color.White.copy(.45f), fontSize = 12.sp)
                TextButton(onClick = { register = !register; error = null }) { Text(if (register) "Sign in" else "Create account", color = Cyan) }
            }
        }
    }
}
