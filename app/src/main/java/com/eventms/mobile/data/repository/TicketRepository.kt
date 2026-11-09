package com.eventms.mobile.data.repository

import com.eventms.mobile.core.Outcome
import com.eventms.mobile.core.outcomeOf
import com.eventms.mobile.data.local.EventDao
import com.eventms.mobile.data.local.TicketDao
import com.eventms.mobile.data.remote.NeonDataApi
import com.eventms.mobile.data.remote.PaymentsApi
import com.eventms.mobile.data.remote.dto.BookingDto
import com.eventms.mobile.data.remote.dto.BookingIdArgs
import com.eventms.mobile.data.remote.dto.CreateBookingArgs
import com.eventms.mobile.data.remote.dto.EmailArgs
import com.eventms.mobile.data.remote.dto.EventDto
import com.eventms.mobile.data.remote.dto.MpesaInitiateRequest
import com.eventms.mobile.data.remote.dto.PaymentInitiateRequest
import com.eventms.mobile.domain.PaymentStatus
import com.eventms.mobile.domain.Ticket
import com.eventms.mobile.domain.toDomain
import com.eventms.mobile.domain.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buying and holding tickets.
 *
 * Bookings are keyed by email, matching the shared schema, so a ticket bought
 * here is the same row the web app's dashboard shows.
 */
@Singleton
class TicketRepository @Inject constructor(
    private val api: NeonDataApi,
    private val payments: PaymentsApi,
    private val ticketDao: TicketDao,
    private val eventDao: EventDao
) {

    val myTickets: Flow<List<Ticket>> =
        ticketDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun ticket(bookingId: String): Flow<Ticket?> =
        ticketDao.observeOne(bookingId).map { it?.toDomain() }

    /** Pulls every booking made with [email] and caches it for offline display. */
    suspend fun refreshTickets(email: String): Outcome<Int> = outcomeOf {
        val bookings = api.getBookingsByEmail(EmailArgs(email.trim().lowercase()))
        ticketDao.upsertAll(bookings.map { it.toEntity(eventFor(it.eventId)) })
        bookings.size
    }

    /**
     * Holds the seats. The SQL function checks availability, decrements it and
     * issues the ticket number in one transaction, so two phones racing for the
     * last ticket cannot both win.
     */
    suspend fun createBooking(
        eventId: Long,
        email: String,
        phone: String,
        quantity: Int
    ): Outcome<BookingDto> = outcomeOf {
        val booking = api.createBooking(
            CreateBookingArgs(
                eventId = eventId,
                email = email.trim().lowercase(),
                phone = phone.trim(),
                quantity = quantity
            )
        ).firstOrNull() ?: throw IllegalStateException("The booking could not be created")

        ticketDao.upsert(booking.toEntity(eventFor(eventId)))
        // Availability just changed; refresh the cached event so the UI agrees.
        runCatching {
            api.getEventById(id = "eq.$eventId").firstOrNull()?.let {
                eventDao.upsert(it.toEntity(ownedByOrganizer = false))
            }
        }
        booking
    }

    /**
     * Opens a checkout and returns the URL to send the customer to.
     *
     * Returns null when the booking is already paid, so a resumed payment does
     * not bounce the customer into a second checkout for a ticket they own.
     */
    suspend fun startPayment(bookingId: String): Outcome<String?> = outcomeOf {
        val response = payments.initiate(PaymentInitiateRequest(bookingId))
        if (response.alreadyPaid || response.paymentStatus == "PAID") {
            refreshBooking(bookingId)
            null
        } else {
            response.redirectUrl
                ?: throw IllegalStateException(
                    response.error ?: "The checkout could not be opened"
                )
        }
    }

    /**
     * Sends an M-Pesa PIN prompt to [phone].
     *
     * Nothing opens in a browser — the customer confirms on their handset and
     * the app polls [checkPaymentStatus]. Returns the message the provider
     * wants shown while they do that.
     */
    suspend fun startMpesaPayment(bookingId: String, phone: String): Outcome<String?> =
        outcomeOf {
            val response = payments.initiateMpesa(MpesaInitiateRequest(bookingId, phone.trim()))
            if (response.alreadyPaid || response.paymentStatus == "PAID") {
                refreshBooking(bookingId)
                null
            } else {
                response.message ?: "Check your phone and enter your M-Pesa PIN."
            }
        }

    /**
     * Asks the gateway to re-check the payment and sync the booking.
     *
     * The app cannot mark a booking paid itself — that permission is revoked in
     * the database — so this is the only route from PENDING to PAID.
     */
    suspend fun checkPaymentStatus(bookingId: String): Outcome<PaymentStatus> = outcomeOf {
        val response = payments.status(PaymentInitiateRequest(bookingId))
        // The gateway has just written the booking, so re-read it either way.
        refreshBooking(bookingId)
        PaymentStatus.from(response.paymentStatus)
    }

    /** Re-reads one booking — picks up a check-in or a payment made elsewhere. */
    suspend fun refreshBooking(bookingId: String): Outcome<Ticket> = outcomeOf {
        val booking = api.getBookingById(BookingIdArgs(bookingId)).firstOrNull()
            ?: throw IllegalStateException("Booking not found")
        val entity = booking.toEntity(eventFor(booking.eventId))
        ticketDao.upsert(entity)
        entity.toDomain()
    }

    /**
     * The event behind a booking, for the denormalised copy on the ticket.
     * Prefers the cache; a finished event may no longer be published.
     */
    private suspend fun eventFor(eventId: Long): EventDto? {
        eventDao.findById(eventId)?.let { cached ->
            return EventDto(
                id = cached.id,
                title = cached.title,
                location = cached.location,
                startDate = cached.startDate
            )
        }
        return runCatching { api.getEventById(id = "eq.$eventId").firstOrNull() }.getOrNull()
            ?.also { eventDao.upsert(it.toEntity(ownedByOrganizer = false)) }
    }

    suspend fun clearCachedTickets() = ticketDao.clear()
}
