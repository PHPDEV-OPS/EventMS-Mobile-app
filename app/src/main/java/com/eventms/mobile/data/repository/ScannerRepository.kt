package com.eventms.mobile.data.repository

import com.eventms.mobile.core.Outcome
import com.eventms.mobile.core.outcomeOf
import com.eventms.mobile.data.local.PendingCheckInDao
import com.eventms.mobile.data.local.PendingCheckInEntity
import com.eventms.mobile.data.local.RosterDao
import com.eventms.mobile.data.remote.NeonDataApi
import com.eventms.mobile.data.remote.dto.EventIdArgs
import com.eventms.mobile.data.remote.dto.VerifyTicketArgs
import com.eventms.mobile.domain.Attendee
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.PaymentStatus
import com.eventms.mobile.domain.ScanOutcome
import com.eventms.mobile.domain.ScanResult
import com.eventms.mobile.domain.toAttendee
import com.eventms.mobile.domain.toOutcome
import com.eventms.mobile.domain.toRosterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The gate scanner.
 *
 * Online, the database is the authority: `api_verify_ticket` checks and admits
 * inside one transaction, so two phones cannot both admit the same ticket.
 * Offline, the device falls back to a roster downloaded beforehand and queues
 * the check-in — the trade-off being that a duplicate scanned on a *different*
 * offline phone is only caught once both have synced.
 */
@Singleton
class ScannerRepository @Inject constructor(
    private val api: NeonDataApi,
    private val rosterDao: RosterDao,
    private val pendingDao: PendingCheckInDao
) {

    fun attendees(eventId: Long): Flow<List<Attendee>> =
        rosterDao.observeForEvent(eventId).map { rows -> rows.map { it.toAttendee() } }

    fun checkedInCount(eventId: Long): Flow<Int> = rosterDao.observeCheckedInCount(eventId)
    fun totalCount(eventId: Long): Flow<Int> = rosterDao.observeTotalCount(eventId)
    val pendingSyncCount: Flow<Int> = pendingDao.observeUnsyncedCount()

    /** Downloads the event's bookings so the scanner works without signal. */
    suspend fun syncRoster(eventId: Long): Outcome<Int> = outcomeOf {
        val bookings = api.getEventBookings(EventIdArgs(eventId))
        rosterDao.upsertAll(bookings.map { it.toRosterEntry() })
        bookings.size
    }

    /**
     * Verify a scanned code, admitting the holder if it is good.
     *
     * Only a genuine connectivity failure falls back to the local roster — an
     * HTTP error is a real answer from the server and is reported as such.
     */
    suspend fun verify(rawCode: String, eventId: Long): ScanOutcome {
        val code = parseScannedCode(rawCode)

        return try {
            val response = api.verifyTicket(
                VerifyTicketArgs(code = code, eventId = eventId, checkIn = true)
            )
            val outcome = response.toOutcome()

            // Keep the local roster in step so the guest list reflects the scan.
            response.booking?.id?.takeIf { outcome.admitted }?.let { bookingId ->
                rosterDao.markCheckedIn(
                    bookingId,
                    response.checkedInAt ?: DateTimes.toApi(LocalDateTime.now())
                )
            }
            outcome
        } catch (e: IOException) {
            verifyOffline(code, eventId)
        }
    }

    /** Matches against the downloaded roster and queues the check-in. */
    private suspend fun verifyOffline(code: String, eventId: Long): ScanOutcome {
        val entry = rosterDao.findByTicketNumber(code)
            ?: rosterDao.findByBookingId(code)
            ?: return ScanOutcome(
                result = ScanResult.NOT_FOUND,
                message = "Offline — no ticket matching this code in the downloaded list",
                pendingSync = true
            )

        val attendee = entry.toAttendee()

        if (entry.eventId != eventId) {
            return ScanOutcome(
                ScanResult.WRONG_EVENT,
                "Offline — this ticket is for a different event",
                pendingSync = true
            )
        }
        if (entry.status == "CANCELLED") {
            return ScanOutcome(
                ScanResult.CANCELLED,
                "Offline — this booking was cancelled",
                pendingSync = true
            )
        }
        if (attendee.paymentStatus != PaymentStatus.PAID) {
            return ScanOutcome(
                ScanResult.NOT_PAID,
                "Offline — this ticket is unpaid",
                pendingSync = true
            )
        }
        if (attendee.isCheckedIn) {
            return ScanOutcome(
                result = ScanResult.ALREADY_CHECKED_IN,
                message = "Offline — already admitted at ${DateTimes.full(attendee.checkedInAt)}",
                ticketNumber = entry.ticketNumber,
                attendeeEmail = entry.email,
                quantity = entry.quantity,
                checkedInAt = attendee.checkedInAt,
                pendingSync = true
            )
        }

        val now = LocalDateTime.now()
        rosterDao.markCheckedIn(entry.bookingId, DateTimes.toApi(now))
        pendingDao.enqueue(
            PendingCheckInEntity(
                code = code,
                eventId = eventId,
                scannedAt = System.currentTimeMillis()
            )
        )

        return ScanOutcome(
            result = ScanResult.VALID,
            message = "Admitted offline — ${entry.quantity} " +
                if (entry.quantity == 1) "ticket" else "tickets",
            ticketNumber = entry.ticketNumber,
            attendeeEmail = entry.email,
            quantity = entry.quantity,
            checkedInAt = now,
            pendingSync = true
        )
    }

    /** Pushes queued offline check-ins. Safe to call repeatedly. */
    suspend fun syncPendingCheckIns(): Outcome<Int> = outcomeOf {
        val queued = pendingDao.unsynced()
        var pushed = 0

        for (entry in queued) {
            try {
                api.verifyTicket(
                    VerifyTicketArgs(
                        code = entry.code,
                        eventId = entry.eventId,
                        checkIn = true
                    )
                )
                // ALREADY_CHECKED_IN counts as success here: it means the server
                // had already recorded this admission.
                pendingDao.markSynced(entry.id)
                pushed++
            } catch (e: IOException) {
                // Still offline. Leave the rest queued and stop trying for now.
                break
            } catch (e: Exception) {
                pendingDao.markFailed(entry.id, e.message ?: "Sync failed")
            }
        }

        pendingDao.clearSynced()
        pushed
    }

    companion object {
        /**
         * Pulls the ticket number out of a scanned payload.
         *
         * The app writes `EVENTMS|<ticketNumber>|<bookingId>|<eventId>` into the
         * QR, but a code typed in by hand, or a bare ticket number printed on a
         * paper ticket, has to work too.
         */
        fun parseScannedCode(raw: String): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith(QR_PREFIX)) return trimmed
            val rest = trimmed.removePrefix(QR_PREFIX)
            val end = rest.indexOf('|')
            return (if (end >= 0) rest.substring(0, end) else rest).trim()
        }

        private const val QR_PREFIX = "EVENTMS|"
    }
}
