package com.eventms.mobile.domain

import java.time.LocalDateTime

/** An event as the UI needs it: dates parsed, money as a plain Double. */
data class Event(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val ticketPrice: Double,
    val totalTickets: Int,
    val availableTickets: Int,
    val status: EventStatus,
    val organizerId: Long,
    val imageUrl: String?,
    val category: String?
) {
    val isSoldOut: Boolean get() = availableTickets <= 0
    val isFree: Boolean get() = ticketPrice <= 0.0
    val isPast: Boolean get() = endDate?.isBefore(LocalDateTime.now()) ?: false
    val isOnSale: Boolean get() = status == EventStatus.PUBLISHED && !isSoldOut && !isPast

    val ticketsSold: Int get() = (totalTickets - availableTickets).coerceAtLeast(0)

    /** True when the organizer pinned a precise venue rather than only naming it. */
    val hasVenuePin: Boolean get() = latitude != null && longitude != null

    /**
     * What to actually show as the cover. An organizer who skips the image
     * should not leave a blank card, so a stock still for the category stands in.
     */
    val coverImageUrl: String
        get() = imageUrl?.takeIf { it.isNotBlank() }
            ?: EventCategories.defaultCoverFor(category)
}

enum class EventStatus {
    DRAFT, PUBLISHED, CANCELLED, COMPLETED;

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

/** A booking from the attendee's point of view — their ticket. */
data class Ticket(
    val bookingId: String,
    val eventId: Long,
    val email: String,
    val phone: String,
    val quantity: Int,
    val status: BookingStatus,
    val paymentStatus: PaymentStatus,
    val totalAmount: Double,
    val ticketNumber: String?,
    val confirmedAt: LocalDateTime?,
    val checkedInAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val eventTitle: String?,
    val eventLocation: String?,
    val eventStartDate: LocalDateTime?
) {
    val isPaid: Boolean get() = paymentStatus == PaymentStatus.PAID
    val isUsed: Boolean get() = checkedInAt != null

    /**
     * Payload encoded in the ticket QR. The gate parses the ticket number out of
     * this; the extra fields let a scanner show context before it has a roster.
     */
    val qrPayload: String
        get() = "EVENTMS|${ticketNumber ?: bookingId}|$bookingId|$eventId"
}

enum class BookingStatus {
    PENDING, CONFIRMED, CANCELLED;

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class PaymentStatus {
    PENDING, PAID, FAILED, REFUNDED;

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

/** One row of an event's attendee roster, as the organizer sees it. */
data class Attendee(
    val bookingId: String,
    val ticketNumber: String?,
    val email: String,
    val quantity: Int,
    val paymentStatus: PaymentStatus,
    val checkedInAt: LocalDateTime?
) {
    val isCheckedIn: Boolean get() = checkedInAt != null
}

/** What the scanner decided about a ticket. Mirrors the backend's Result enum. */
enum class ScanResult {
    VALID, ALREADY_CHECKED_IN, NOT_PAID, CANCELLED, WRONG_EVENT, NOT_FOUND;

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name == raw } ?: NOT_FOUND
    }
}

data class ScanOutcome(
    val result: ScanResult,
    val message: String,
    val ticketNumber: String? = null,
    val attendeeEmail: String? = null,
    val quantity: Int? = null,
    val eventTitle: String? = null,
    val checkedInAt: LocalDateTime? = null,
    /** True when admitted from the offline roster and still queued for the server. */
    val pendingSync: Boolean = false
) {
    val admitted: Boolean get() = result == ScanResult.VALID
}

data class OrganizerStats(
    val totalEvents: Int = 0,
    val publishedEvents: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalTicketsSold: Int = 0
)
