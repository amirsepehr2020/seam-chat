package amir.sepehr.seamchat.chat
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
interface ChatApi{
 @GET("api/v1/conversations/{id}/messages") suspend fun messages(@Path("id") id:String):Response<List<MessageDto>>
 @POST("api/v1/conversations/{id}/messages") suspend fun send(@Path("id") id:String,@Body request:SendMessageRequest):Response<MessageDto>
 @POST("api/v1/messages/{messageId}/reply") suspend fun reply(@Path("messageId") id:String,@Body request:ReplyMessageRequest):Response<MessageDto>
 @POST("api/v1/messages/{messageId}/edit") suspend fun edit(@Path("messageId") id:String,@Body request:EditMessageRequest):Response<MessageDto>
 @POST("api/v1/messages/{messageId}/delete") suspend fun delete(@Path("messageId") id:String):Response<ActionResponse>
 @POST("api/v1/messages/{messageId}/forward") suspend fun forward(@Path("messageId") id:String,@Body request:ForwardMessageRequest):Response<MessageDto>
 @POST("api/v1/messages/{messageId}/reaction") suspend fun react(@Path("messageId") id:String,@Body request:ReactionRequest):Response<ReactionResponse>
 @POST("api/v1/messages/{messageId}/reaction/remove") suspend fun unreact(@Path("messageId") id:String,@Body request:ReactionRequest):Response<ReactionResponse>
}
data class MessageDto(val id:String,val conversationId:String,val senderId:String,val body:String?,val type:String,val createdAt:Long,val editedAt:Long?=null,val deleted:Boolean=false,val replyToMessageId:String?=null)
data class SendMessageRequest(val body:String,val type:String="text")
data class ReplyMessageRequest(val body:String,val type:String="text")
data class EditMessageRequest(val body:String)
data class ForwardMessageRequest(val conversationId:String)
data class ReactionRequest(val reaction:String)
data class ActionResponse(val ok:Boolean,val messageId:String?=null)
data class ReactionResponse(val ok:Boolean,val added:Boolean?=null,val removed:Boolean?=null,val reaction:String?=null)
