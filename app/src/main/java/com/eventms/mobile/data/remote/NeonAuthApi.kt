package com.eventms.mobile.data.remote

import com.eventms.mobile.data.remote.dto.NeonSessionDto
import com.eventms.mobile.data.remote.dto.NeonSignInRequest
import com.eventms.mobile.data.remote.dto.NeonSignUpRequest
import com.eventms.mobile.data.remote.dto.NeonTokenDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Neon Auth, which is a hosted Better Auth instance.
 *
 * Two things about this API drive the client design:
 *  - Every call must carry an `Origin` header matching a trusted domain
 *    registered on the Neon branch; without it Better Auth answers
 *    MISSING_ORIGIN / INVALID_ORIGIN. [NeonAuthOriginInterceptor] adds it.
 *  - The session lives in a cookie, not a bearer header. Sign-in sets it and
 *    `/token` exchanges it for the short-lived (~15 min) EdDSA JWT that the
 *    EventMS backend actually validates, so the cookie must persist.
 */
interface NeonAuthApi {

    @POST("sign-up/email")
    suspend fun signUp(@Body request: NeonSignUpRequest): Response<NeonSessionDto>

    @POST("sign-in/email")
    suspend fun signIn(@Body request: NeonSignInRequest): Response<NeonSessionDto>

    /** Exchanges the session cookie for a JWT. Call again once it expires. */
    @GET("token")
    suspend fun getToken(): Response<NeonTokenDto>

    @GET("get-session")
    suspend fun getSession(): Response<NeonSessionDto>

    @POST("sign-out")
    suspend fun signOut(): Response<Unit>
}
