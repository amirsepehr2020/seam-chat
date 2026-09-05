package amir.sepehr.seamchat.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF11182E), Bg))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Violet, Cyan))),
                contentAlignment = Alignment.Center
            ) { Text("S", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(18.dp))
            Text("SEAM CHAT", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (register) "Create your private account" else "Welcome back to your private space",
                color = Color.White.copy(.5f), fontSize = 13.sp
            )
            Spacer(Modifier.height(26.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Card).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username") },
                    shape = RoundedCornerShape(18.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(18.dp)
                )
                if (register) {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Invite code") },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAuthenticated,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) {
                    Text(if (register) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (register) "Already have an account?" else "New to SEAM CHAT?",
                    color = Color.White.copy(.45f), fontSize = 12.sp
                )
                TextButton(onClick = { register = !register }) {
                    Text(if (register) "Sign in" else "Create account", color = Cyan)
                }
            }
        }
    }
}
