package amir.sepehr.seamchat

import kotlinx.serialization.Serializable

@Serializable data class AuthRequest(val username: String, val password: String, val inviteCode: String? = null, val displayName: String? = null)
@Serializable data class UserDto(val id: String, val username: String, val displayName: String, val avatarUrl: String? = null)
@Serializable data class AuthResponse(val user: UserDto, val token: String, val expiresAt: Long)
@Serializable data class MessageDto(val id: String, val conversationId: String, val senderId: String, val body: String?, val type: String, val createdAt: Long)
@Serializable data class SendMessageRequest(val body: String, val type: String = "text")
@Serializable data class ConversationDto(val id: String, val type: String, val title: String?, val createdAt: Long, val lastMessage: MessageDto? = null, val unreadCount: Int = 0, val otherUser: UserDto? = null)
@Serializable data class CreateDirectConversationRequest(val userId: String)
