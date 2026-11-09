package com.eventms.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "eventms_session")

/**
 * What the app remembers between launches: who is signed in, their local
 * profile id and role, and the cached Neon Auth JWT.
 */
@Singleton
class SessionStore @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val JWT = stringPreferencesKey("neon_jwt")
        val JWT_EXPIRES_AT = longPreferencesKey("neon_jwt_expires_at")
        val SIGNED_IN = booleanPreferencesKey("signed_in")
        val ROLE = stringPreferencesKey("role")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val USER_ID = longPreferencesKey("user_id")
    }

    val session: Flow<Session> = context.dataStore.data.map { it.toSession() }

    private fun Preferences.toSession() = Session(
        signedIn = this[Keys.SIGNED_IN] ?: false,
        role = this[Keys.ROLE] ?: ROLE_ATTENDEE,
        email = this[Keys.EMAIL],
        displayName = this[Keys.DISPLAY_NAME],
        userId = this[Keys.USER_ID]
    )

    suspend fun current(): Session = session.first()

    suspend fun cachedJwt(): String? {
        val prefs = context.dataStore.data.first()
        val token = prefs[Keys.JWT] ?: return null
        val expiresAt = prefs[Keys.JWT_EXPIRES_AT] ?: 0L
        // Treat a token inside its last half-minute as already gone so a request
        // is never sent with one that expires mid-flight.
        return if (System.currentTimeMillis() < expiresAt - 30_000) token else null
    }

    suspend fun saveJwt(token: String, expiresAtMillis: Long) {
        context.dataStore.edit {
            it[Keys.JWT] = token
            it[Keys.JWT_EXPIRES_AT] = expiresAtMillis
        }
    }

    suspend fun clearJwt() {
        context.dataStore.edit {
            it.remove(Keys.JWT)
            it.remove(Keys.JWT_EXPIRES_AT)
        }
    }

    suspend fun setSignedIn(userId: Long, email: String, displayName: String?, role: String) {
        context.dataStore.edit {
            it[Keys.SIGNED_IN] = true
            it[Keys.USER_ID] = userId
            it[Keys.EMAIL] = email
            it[Keys.ROLE] = role
            if (!displayName.isNullOrBlank()) it[Keys.DISPLAY_NAME] = displayName
        }
    }

    suspend fun setRole(role: String) {
        context.dataStore.edit { it[Keys.ROLE] = role }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        const val ROLE_ATTENDEE = "ROLE_ATTENDEE"
        const val ROLE_ORGANIZER = "ROLE_ORGANIZER"
        const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}

data class Session(
    val signedIn: Boolean = false,
    val role: String = SessionStore.ROLE_ATTENDEE,
    val email: String? = null,
    val displayName: String? = null,
    val userId: Long? = null
) {
    val isOrganizer: Boolean
        get() = role == SessionStore.ROLE_ORGANIZER || role == SessionStore.ROLE_ADMIN
}
