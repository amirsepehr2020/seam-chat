package amir.sepehr.seamchat

data class ConversationDto(
    val id: String,
    val title: String? = null,
    val type: String = "direct",
    val createdAt: Long = 0L,
    val otherUser: UserDto? = null
)

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String? = null
)

data class CreateDirectConversationRequest(val userId: String)
