package com.eventms.mobile.data.repository

import com.eventms.mobile.core.Outcome
import com.eventms.mobile.core.outcomeOf
import com.eventms.mobile.data.prefs.Session
import com.eventms.mobile.data.prefs.SessionStore
import com.eventms.mobile.data.remote.NeonAuthApi
import com.eventms.mobile.data.remote.NeonDataApi
import com.eventms.mobile.data.remote.NeonTokenProvider
import com.eventms.mobile.data.remote.PersistentCookieJar
import com.eventms.mobile.data.remote.dto.NeonSignInRequest
import com.eventms.mobile.data.remote.dto.NeonSignUpRequest
import com.eventms.mobile.data.remote.dto.ProfileDto
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accounts, end to end in Neon: Neon Auth holds the credential, and a SQL
 * function on the other side of the Data API mints the matching row in the
 * shared `users` table — the same table the web app reads.
 *
 * A new account is an attendee until it explicitly asks to become an organizer.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val neonAuth: NeonAuthApi,
    private val api: NeonDataApi,
    private val sessionStore: SessionStore,
    private val tokenProvider: NeonTokenProvider,
    private val cookieJar: PersistentCookieJar
) {

    val session: Flow<Session> = sessionStore.session

    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        asOrganizer: Boolean
    ): Outcome<ProfileDto> = outcomeOf {
        val response = neonAuth.signUp(NeonSignUpRequest(email.trim(), password, name.trim()))
        if (!response.isSuccessful) {
            throw IllegalStateException(readAuthError(response, "Could not create that account"))
        }

        // The sign-up response sets the session cookie; turn it into a JWT.
        tokenProvider.refresh()
            ?: throw IllegalStateException("Signed up, but could not obtain an access token")

        finishSignIn(email.trim(), name.trim(), asOrganizer)
    }

    suspend fun signIn(
        email: String,
        password: String,
        asOrganizer: Boolean = false
    ): Outcome<ProfileDto> = outcomeOf {
        val response = neonAuth.signIn(NeonSignInRequest(email.trim(), password))
        if (!response.isSuccessful) {
            throw IllegalStateException(readAuthError(response, "Wrong email or password"))
        }

        val displayName = response.body()?.user?.name

        tokenProvider.refresh()
            ?: throw IllegalStateException("Signed in, but could not obtain an access token")

        finishSignIn(email.trim(), displayName, asOrganizer)
    }

    /**
     * Provisions the local profile and resolves the role, asking for organizer
     * rights when the person came in through the organizer entrance.
     */
    private suspend fun finishSignIn(
        email: String,
        displayName: String?,
        asOrganizer: Boolean
    ): ProfileDto {
        val profile = if (asOrganizer) {
            api.becomeOrganizer().first()
        } else {
            api.ensureProfile().first()
        }

        sessionStore.setSignedIn(
            userId = profile.id,
            email = profile.email.ifBlank { email },
            displayName = displayName?.takeIf { it.isNotBlank() } ?: profile.displayName,
            role = profile.role
        )
        return profile
    }

    /** Promotes an already signed-in attendee to organizer. */
    suspend fun becomeOrganizer(): Outcome<ProfileDto> = outcomeOf {
        val profile = api.becomeOrganizer().first()
        sessionStore.setSignedIn(
            userId = profile.id,
            email = profile.email,
            displayName = profile.displayName,
            role = profile.role
        )
        profile
    }

    /** Re-reads the profile; also repairs a session whose JWT has gone stale. */
    suspend fun refreshProfile(): Outcome<ProfileDto> = outcomeOf {
        val profile = api.ensureProfile().first()
        sessionStore.setSignedIn(
            userId = profile.id,
            email = profile.email,
            displayName = profile.displayName,
            role = profile.role
        )
        profile
    }

    suspend fun signOut() {
        runCatching { neonAuth.signOut() }
        cookieJar.clear()
        sessionStore.clearJwt()
        sessionStore.signOut()
    }

    private fun readAuthError(response: Response<*>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull() ?: return fallback
        return runCatching {
            val json = JSONObject(raw)
            when (json.optString("code")) {
                "USER_ALREADY_EXISTS" -> "An account with that email already exists"
                "INVALID_EMAIL_OR_PASSWORD" -> "Wrong email or password"
                "PASSWORD_TOO_SHORT" ->
                    "Password must be at least 8 characters"
                // A misconfigured trusted origin is a deployment problem; the
                // user cannot act on the detail, so do not describe it to them.
                "INVALID_ORIGIN", "MISSING_ORIGIN" ->
                    "Sign-in is unavailable right now. Please try again later."
                else -> json.optString("message").ifBlank { fallback }
            }
        }.getOrDefault(fallback)
    }
}
