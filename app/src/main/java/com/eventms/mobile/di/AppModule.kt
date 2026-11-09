package com.eventms.mobile.di

import android.content.Context
import androidx.room.Room
import com.eventms.mobile.BuildConfig
import com.eventms.mobile.data.local.EventDao
import com.eventms.mobile.data.local.EventMsDatabase
import com.eventms.mobile.data.local.PendingCheckInDao
import com.eventms.mobile.data.local.RosterDao
import com.eventms.mobile.data.local.TicketDao
import com.eventms.mobile.data.remote.MediaApi
import com.eventms.mobile.data.remote.NeonAuthApi
import com.eventms.mobile.data.remote.NeonAuthInterceptor
import com.eventms.mobile.data.remote.NeonAuthOriginInterceptor
import com.eventms.mobile.data.remote.NeonDataApi
import com.eventms.mobile.data.remote.NeonTokenProvider
import com.eventms.mobile.data.remote.PaymentsApi
import com.eventms.mobile.data.remote.PersistentCookieJar
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NeonAuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DataApiClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    /**
     * Google Places, for venue autocomplete.
     *
     * Initialised lazily here rather than in Application.onCreate so a missing
     * or unentitled key costs nothing until an organizer actually opens the
     * event editor.
     */
    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.MAPS_API_KEY)
        }
        return Places.createClient(context)
    }

    // ------------------------------------------------------------ Room

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EventMsDatabase =
        Room.databaseBuilder(context, EventMsDatabase::class.java, EventMsDatabase.NAME)
            // A cache and a scan queue; rebuilding beats migrating during coursework.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideEventDao(db: EventMsDatabase): EventDao = db.eventDao()
    @Provides fun provideTicketDao(db: EventMsDatabase): TicketDao = db.ticketDao()
    @Provides fun provideRosterDao(db: EventMsDatabase): RosterDao = db.rosterDao()
    @Provides fun providePendingCheckInDao(db: EventMsDatabase): PendingCheckInDao =
        db.pendingCheckInDao()

    // --------------------------------------------------------- networking

    @Provides
    @Singleton
    fun provideLogging(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * Client for Neon Auth. Carries the cookie jar and the Origin header, and
     * deliberately has no auth interceptor — it is what mints the token.
     */
    @Provides
    @Singleton
    @NeonAuthClient
    fun provideNeonAuthOkHttp(
        cookieJar: PersistentCookieJar,
        logging: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(NeonAuthOriginInterceptor())
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideNeonAuthApi(
        @NeonAuthClient client: OkHttpClient,
        tokenProvider: NeonTokenProvider
    ): NeonAuthApi {
        // Better Auth mounts its routes directly under the auth base; the
        // trailing slash keeps Retrofit from dropping the /neondb/auth segment.
        val baseUrl = BuildConfig.NEON_AUTH_BASE_URL.trimEnd('/') + "/"

        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeonAuthApi::class.java)

        // Closes the loop: the interceptor on the Data API client needs to mint
        // tokens, but building that client must not depend on this one.
        tokenProvider.api = api
        return api
    }

    /** Client for the Neon Data API: every call carries the Neon Auth JWT. */
    @Provides
    @Singleton
    @DataApiClient
    fun provideDataApiOkHttp(
        authInterceptor: NeonAuthInterceptor,
        logging: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideNeonDataApi(@DataApiClient client: OkHttpClient): NeonDataApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.NEON_DATA_API_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeonDataApi::class.java)

    /**
     * Payment gateway. Shares the authenticated client so the gateway can prove
     * which account is asking before it opens a checkout.
     */
    @Provides
    @Singleton
    fun providePaymentsApi(@DataApiClient client: OkHttpClient): PaymentsApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.NEON_PAYMENTS_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PaymentsApi::class.java)

    /**
     * Cover image uploads. Gets a longer write timeout than the others — a
     * few hundred kilobytes over a phone connection is not instant.
     */
    @Provides
    @Singleton
    fun provideMediaApi(
        authInterceptor: NeonAuthInterceptor,
        logging: HttpLoggingInterceptor
    ): MediaApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.NEON_MEDIA_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MediaApi::class.java)
    }
}
