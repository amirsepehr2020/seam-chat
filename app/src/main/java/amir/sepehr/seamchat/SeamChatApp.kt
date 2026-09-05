package amir.sepehr.seamchat

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import amir.sepehr.seamchat.auth.AuthRepository
import amir.sepehr.seamchat.auth.SeamAuthScreen

@Composable
fun SeamChatApp() {
    val context = LocalContext.current
    val repository = remember { AuthRepository(context) }
    var checking by remember { mutableStateOf(true) }
    var authenticated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authenticated = repository.hasSession()
        checking = false
    }

    when {
        checking -> SplashScreen()
        authenticated -> SeamChatHome(onLogout = { authenticated = false })
        else -> SeamAuthScreen(onAuthenticated = { authenticated = true })
    }
}
