package com.eventms.mobile.data.remote

import android.content.Context
import android.util.Base64
import com.eventms.mobile.data.prefs.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Origin header required by Better Auth.
 *
 * Better Auth refuses requests without an Origin (MISSING_ORIGIN) and rejects
 * any Origin that is not a trusted domain on the Neon branch (INVALID_ORIGIN).
 * A native app sends none by default, so this pins one. It must stay in sync
 * with `neon neon-auth domain list`.
 */
class NeonAuthOriginInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("Origin", TRUSTED_ORIGIN)
                .build()
        )

    companion object {
        const val TRUSTED_ORIGIN = "https://eventms.app"
    }
}

/**
 * Persists Neon Auth's session cookie across app launches.
 *
 * The cookie value is the session token plus an HMAC signature, and only the
 * unsigned half is echoed in the sign-in JSON body — so the cookie cannot be
 * rebuilt from that and has to be stored verbatim. Attributes are written out
 * field by field because OkHttp's `Cookie.toString()` emits only `name=value`.
 * SharedPreferences rather than DataStore because [CookieJar] is synchronous.
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    context: Context
) : CookieJar {

    private val prefs = context.getSharedPreferences("neon_auth_cookies", Context.MODE_PRIVATE)
    private val cache = linkedMapOf<String, Cookie>()

    init {
        prefs.all.values.forEach { stored ->
            (stored as? String)?.let { json ->
                runCatching { deserialize(json) }.getOrNull()?.let { cache[it.name] = it }
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = prefs.edit()
        cookies.forEach { cookie ->
            if (cookie.expiresAt < System.currentTimeMillis()) {
                cache.remove(cookie.name)
                editor.remove(cookie.name)
            } else {
                cache[cookie.name] = cookie
                editor.putString(cookie.name, serialize(cookie))
            }
        }
        editor.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = cache.filterValues { it.expiresAt < now }.keys.toList()
        if (expired.isNotEmpty()) {
            val editor = prefs.edit()
            expired.forEach {
                cache.remove(it)
                editor.remove(it)
            }
            editor.apply()
        }
        return cache.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        prefs.edit().clear().apply()
    }

    private fun serialize(cookie: Cookie): String = JSONObject().apply {
        put("name", cookie.name)
        put("value", cookie.value)
        put("domain", cookie.domain)
        put("path", cookie.path)
        put("expiresAt", cookie.expiresAt)
        put("secure", cookie.secure)
        put("httpOnly", cookie.httpOnly)
        put("hostOnly", cookie.hostOnly)
    }.toString()

    private fun deserialize(json: String): Cookie {
        val obj = JSONObject(json)
        return Cookie.Builder()
            .name(obj.getString("name"))
            .value(obj.getString("value"))
            .path(obj.optString("path", "/"))
            .expiresAt(obj.optLong("expiresAt", Long.MAX_VALUE))
            .apply {
                val domain = obj.getString("domain")
                // hostOnly and domain-wide cookies are set through different
                // builder calls; picking the wrong one changes which hosts match.
                if (obj.optBoolean("hostOnly")) hostOnlyDomain(domain) else domain(domain)
                if (obj.optBoolean("secure")) secure()
                if (obj.optBoolean("httpOnly")) httpOnly()
            }
            .build()
    }
}

/**
 * Attaches the Neon Auth JWT to Data API calls, fetching a fresh one when the
 * cached token has expired.
 *
 * Neon's JWTs last about 15 minutes while the session cookie lasts a week, so
 * the exchange at /token is the normal path, not an error path.
 */
@Singleton
class NeonAuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
    private val tokenProvider: NeonTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val token = runBlocking {
            sessionStore.cachedJwt() ?: tokenProvider.refresh()
        } ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("apikey", token)
                .build()
        )
    }
}

/** Exchanges the stored session cookie for a fresh JWT and caches it. */
@Singleton
class NeonTokenProvider @Inject constructor(
    private val sessionStore: SessionStore
) {
    /** Assigned after construction to avoid a Retrofit/OkHttp dependency cycle. */
    @Volatile
    var api: NeonAuthApi? = null

    suspend fun refresh(): String? {
        val client = api ?: return null

        return runCatching {
            val response = client.getToken()
            val token = response.body()?.token
            if (!response.isSuccessful || token.isNullOrBlank()) {
                null
            } else {
                sessionStore.saveJwt(token, expiryOf(token))
                token
            }
        }.getOrNull()
    }

    /**
     * Reads `exp` straight out of the JWT payload. Falls back to a conservative
     * ten minutes if the claim cannot be read, so a malformed token gets
     * refreshed sooner rather than trusted for longer.
     */
    private fun expiryOf(jwt: String): Long = runCatching {
        val payload = jwt.split(".")[1]
        val decoded = String(
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        )
        JSONObject(decoded).getLong("exp") * 1000L
    }.getOrElse { System.currentTimeMillis() + 10 * 60 * 1000L }
}
