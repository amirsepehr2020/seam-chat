package amir.sepehr.seamchat.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConversationScreen(
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val items = (state as? ConversationListState.Ready)?.conversations.orEmpty()
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.lastMessage.contains(query, ignoreCase = true) }

    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, "New chat") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("SEAM CHAT", style = MaterialTheme.typography.headlineLarge)
            Text("Your conversations", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search conversations") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            when (state) {
                ConversationListState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is ConversationListState.Error -> Text("Could not load chats. Pull to retry.")
                is ConversationListState.Ready -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { chat ->
                        ListItem(
                            headlineContent = { Text(chat.title) },
                            supportingContent = { Text(chat.lastMessage, maxLines = 1) },
                            leadingContent = { AssistChip(onClick = {}, label = { Text(chat.avatarLetter) }) },
                            trailingContent = { Column(horizontalAlignment = Alignment.End) { Text(chat.timestamp, style = MaterialTheme.typography.labelSmall); if (chat.unreadCount > 0) Badge { Text(chat.unreadCount.toString()) } } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
