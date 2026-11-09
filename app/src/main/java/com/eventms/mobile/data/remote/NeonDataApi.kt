package com.eventms.mobile.data.remote

import com.eventms.mobile.data.remote.dto.BookingDto
import com.eventms.mobile.data.remote.dto.BookingIdArgs
import com.eventms.mobile.data.remote.dto.CreateBookingArgs
import com.eventms.mobile.data.remote.dto.EmailArgs
import com.eventms.mobile.data.remote.dto.EventDto
import com.eventms.mobile.data.remote.dto.EventIdArgs
import com.eventms.mobile.data.remote.dto.EventWriteDto
import com.eventms.mobile.data.remote.dto.OrganizerStatsDto
import com.eventms.mobile.data.remote.dto.ProfileDto
import com.eventms.mobile.data.remote.dto.TicketVerificationResponseDto
import com.eventms.mobile.data.remote.dto.VerifyTicketArgs
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The app's backend: Neon's Data API (PostgREST) over the shared Neon Postgres.
 *
 * There is no application server in between — the web app's Spring service and
 * this app write the same tables, which is what keeps their records in sync.
 *
 * Two kinds of endpoint appear here:
 *  - `/events`, queried directly. Row-level security decides what is visible:
 *    published events to everyone, plus your own drafts if you are the organizer.
 *  - `/rpc/...`, SQL functions holding the logic that needs to be transactional
 *    (holding seats, admitting a ticket exactly once) or privileged.
 *
 * Every request carries a Neon Auth JWT; the gateway rejects unauthenticated
 * calls outright, so the app requires sign-in.
 */
interface NeonDataApi {

    // ------------------------------------------------------------ events

    /** Published events, soonest first. */
    @GET("events")
    suspend fun getPublishedEvents(
        @Query("select") select: String = "*",
        @Query("status") status: String = "eq.PUBLISHED",
        @Query("order") order: String = "start_date.asc"
    ): List<EventDto>

    /** Everything this organizer owns, drafts included. */
    @GET("events")
    suspend fun getOrganizerEvents(
        @Query("organizer_id") organizerId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "start_date.desc"
    ): List<EventDto>

    @GET("events")
    suspend fun getEventById(
        @Query("id") id: String,
        @Query("select") select: String = "*"
    ): List<EventDto>

    /** Other events by the same organizer — the "past events" rail. */
    @GET("events")
    suspend fun getEventsByOrganizer(
        @Query("organizer_id") organizerId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "start_date.desc"
    ): List<EventDto>

    @Headers("Prefer: return=representation")
    @POST("events")
    suspend fun createEvent(@Body event: EventWriteDto): List<EventDto>

    @Headers("Prefer: return=representation")
    @PATCH("events")
    suspend fun updateEvent(
        @Query("id") id: String,
        @Body event: EventWriteDto
    ): List<EventDto>

    @DELETE("events")
    suspend fun deleteEvent(@Query("id") id: String)

    // ----------------------------------------------------------- profile

    /** Creates or links the local users row behind the Neon Auth token. */
    @POST("rpc/api_ensure_profile")
    suspend fun ensureProfile(@Body body: Map<String, String> = emptyMap()): List<ProfileDto>

    @POST("rpc/api_become_organizer")
    suspend fun becomeOrganizer(@Body body: Map<String, String> = emptyMap()): List<ProfileDto>

    @POST("rpc/api_organizer_stats")
    suspend fun getOrganizerStats(
        @Body body: Map<String, String> = emptyMap()
    ): OrganizerStatsDto

    // ---------------------------------------------------------- bookings

    /** Holds the seats and issues a ticket number, atomically. */
    @POST("rpc/api_create_booking")
    suspend fun createBooking(@Body args: CreateBookingArgs): List<BookingDto>

    // NOTE: there is deliberately no confirm-payment call here. Marking a
    // booking paid is revoked from the client roles in the database; only the
    // payment gateway, which verifies the charge with the provider first, may
    // do it. See PaymentsApi.

    @POST("rpc/api_bookings_by_email")
    suspend fun getBookingsByEmail(@Body args: EmailArgs): List<BookingDto>

    @POST("rpc/api_booking_by_id")
    suspend fun getBookingById(@Body args: BookingIdArgs): List<BookingDto>

    /** The event's guest list. Refused unless you manage the event. */
    @POST("rpc/api_event_bookings")
    suspend fun getEventBookings(@Body args: EventIdArgs): List<BookingDto>

    // ---------------------------------------------------------- scanning

    /** Verifies and admits in one call, so a ticket cannot be used twice. */
    @POST("rpc/api_verify_ticket")
    suspend fun verifyTicket(@Body args: VerifyTicketArgs): TicketVerificationResponseDto
}
