package com.eventms.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Events kept on device so browsing and the organizer's event list still render
 * without a connection.
 */
@Entity(tableName = "cached_events")
data class CachedEventEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String?,
    val location: String,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: String?,
    val endDate: String?,
    val ticketPrice: Double,
    val totalTickets: Int,
    val availableTickets: Int,
    val status: String,
    val organizerId: Long,
    val imageUrl: String?,
    val category: String?,
    /** True for events fetched from the organizer endpoint (includes drafts). */
    val ownedByOrganizer: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * The attendee's own tickets. Denormalised with the event's headline details so
 * a ticket can be shown at the gate with no network at all.
 */
@Entity(tableName = "cached_tickets")
data class CachedTicketEntity(
    @PrimaryKey val bookingId: String,
    val eventId: Long,
    val email: String,
    val phone: String,
    val quantity: Int,
    val status: String,
    val paymentStatus: String,
    val totalAmount: Double,
    val ticketNumber: String?,
    val confirmedAt: String?,
    val checkedInAt: String?,
    val createdAt: String?,
    val eventTitle: String?,
    val eventLocation: String?,
    val eventStartDate: String?
)

/**
 * An event's paid attendees, downloaded before the doors open so the scanner
 * keeps working when the venue has no signal.
 */
@Entity(
    tableName = "roster_entries",
    indices = [Index("eventId"), Index("ticketNumber")]
)
data class RosterEntryEntity(
    @PrimaryKey val bookingId: String,
    val eventId: Long,
    val ticketNumber: String?,
    val email: String,
    val quantity: Int,
    val status: String,
    val paymentStatus: String,
    val checkedInAt: String?,
    val syncedAt: Long = System.currentTimeMillis()
)

/**
 * A scan taken while offline, waiting to be pushed to the backend.
 *
 * The device admits the ticket locally against [RosterEntryEntity] and queues
 * the check-in here; [synced] flips once the server has recorded it.
 */
@Entity(tableName = "pending_check_ins", indices = [Index("code", unique = true)])
data class PendingCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val eventId: Long,
    val scannedAt: Long,
    val synced: Boolean = false,
    val lastError: String? = null
)
