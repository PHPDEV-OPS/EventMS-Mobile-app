package com.eventms.mobile.data.remote

import com.eventms.mobile.data.remote.dto.MediaUploadResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Cover image uploads.
 *
 * The picked image is sent as a raw body rather than multipart — there is only
 * ever one file and no other fields, so the content type header carries
 * everything the server needs. It answers with a plain public URL that goes
 * into the event's `image_url`, which is why the same picture shows up in the
 * web app too.
 */
interface MediaApi {

    @POST("upload")
    suspend fun upload(
        @Header("Content-Type") contentType: String,
        @Body image: RequestBody
    ): MediaUploadResponse
}
