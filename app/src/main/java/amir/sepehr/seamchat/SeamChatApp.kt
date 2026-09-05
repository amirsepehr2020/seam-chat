package amir.sepehr.seamchat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import amir.sepehr.seamchat.auth.SeamAuthScreen

@Composable
fun SeamChatApp() {
    var authenticated by remember { mutableStateOf(false) }

    if (authenticated) {
        SeamChatHome()
    } else {
        SeamAuthScreen(onAuthenticated = { authenticated = true })
    }
}
