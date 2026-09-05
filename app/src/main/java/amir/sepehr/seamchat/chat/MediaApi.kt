package amir.sepehr.seamchat.chat

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MediaApi {
    @Multipart
    @POST("api/v1/media/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("type") type: RequestBody
    ): Response<MediaUploadResponse>
}

data class MediaUploadResponse(
    val id: String,
    val url: String,
    val type: String,
    val originalName: String?
)
