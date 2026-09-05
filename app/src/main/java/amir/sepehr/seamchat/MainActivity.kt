package amir.sepehr.seamchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import amir.sepehr.seamchat.notifications.SeamNotificationCoordinator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeamChatApp() }
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val conversationId = intent?.getStringExtra(SeamNotificationCoordinator.EXTRA_CONVERSATION_ID)
        if (!conversationId.isNullOrBlank()) {
            // Navigation can consume this extra when the conversation host is initialized.
            intent?.removeExtra(SeamNotificationCoordinator.EXTRA_CONVERSATION_ID)
        }
    }
}
