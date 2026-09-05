package amir.sepehr.seamchat.network

import amir.sepehr.seamchat.ConversationDto
import amir.sepehr.seamchat.CreateDirectConversationRequest
import amir.sepehr.seamchat.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ConversationApi {
    @GET("api/v1/users/search")
    suspend fun searchUsers(@Query("q") query: String): List<UserDto>

    @GET("api/v1/conversations")
    suspend fun conversations(): List<ConversationDto>

    @POST("api/v1/conversations/direct")
    suspend fun createDirect(@Body request: CreateDirectConversationRequest): ConversationDto
}
