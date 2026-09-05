package amir.sepehr.seamchat

import kotlinx.serialization.Serializable

@Serializable data class CreateConversationRequest(val title: String? = null, val type: String = "direct", val memberIds: List<String> = emptyList())
@Serializable data class ConversationDto(val id: String, val title: String?, val type: String, val createdAt: Long)
