package com.eventms.mobile.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/*
 * Wire models for the Neon Data API (PostgREST) and Neon Auth.
 *
 * Table rows come back with snake_case column names, so those are mapped
 * explicitly. Timestamps stay as Strings: Postgres returns them without a zone
 * (e.g. "2026-09-13T11:13:48.961687"), which Gson has no adapter for, so they
 * are parsed once at the domain boundary.
 */

// ---------------------------------------------------------------- events

data class EventDto(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("ticket_price") val ticketPrice: BigDecimal = BigDecimal.ZERO,
    @SerializedName("total_tickets") val totalTickets: Int = 0,
    @SerializedName("available_tickets") val availableTickets: Int = 0,
    val status: String = "DRAFT",
    @SerializedName("organizer_id") val organizerId: Long = 0,
    @SerializedName("image_url") val imageUrl: String? = null,
    val category: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Body for inserting or updating an event.
 *
 * `available_tickets` is only sent on insert — on update it would clobber the
 * count that bookings have already drawn down.
 */
data class EventWriteDto(
    val title: String,
    val description: String?,
    val location: String,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String,
    @SerializedName("ticket_price") val ticketPrice: BigDecimal,
    @SerializedName("total_tickets") val totalTickets: Int,
    @SerializedName("available_tickets") val availableTickets: Int? = null,
    val status: String,
    @SerializedName("organizer_id") val organizerId: Long? = null,
    @SerializedName("image_url") val imageUrl: String?,
    val category: String?
)

// -------------------------------------------------------------- bookings

data class BookingDto(
    val id: String = "",
    @SerializedName(value = "event_id", alternate = ["eventId"]) val eventId: Long = 0,
    val email: String = "",
    val phone: String = "",
    val quantity: Int = 0,
    val status: String = "PENDING",
    @SerializedName(value = "payment_status", alternate = ["paymentStatus"])
    val paymentStatus: String = "PENDING",
    @SerializedName(value = "total_amount", alternate = ["totalAmount"])
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "ticket_number", alternate = ["ticketNumber"])
    val ticketNumber: String? = null,
    @SerializedName(value = "confirmed_at", alternate = ["confirmedAt"])
    val confirmedAt: String? = null,
    @SerializedName(value = "checked_in_at", alternate = ["checkedInAt"])
    val checkedInAt: String? = null,
    @SerializedName(value = "checked_in_by", alternate = ["checkedInBy"])
    val checkedInBy: String? = null,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null
)

// ------------------------------------------------------------ RPC bodies

/** Arguments are named after the SQL function's parameters. */
data class CreateBookingArgs(
    @SerializedName("p_event_id") val eventId: Long,
    @SerializedName("p_email") val email: String,
    @SerializedName("p_phone") val phone: String,
    @SerializedName("p_quantity") val quantity: Int
)

data class BookingIdArgs(@SerializedName("p_booking_id") val bookingId: String)

data class EmailArgs(@SerializedName("p_email") val email: String)

data class EventIdArgs(@SerializedName("p_event_id") val eventId: Long)

data class VerifyTicketArgs(
    @SerializedName("p_code") val code: String,
    @SerializedName("p_event_id") val eventId: Long,
    @SerializedName("p_check_in") val checkIn: Boolean = true
)

/** api_verify_ticket returns camelCase JSON built in SQL. */
data class TicketVerificationResponseDto(
    val result: String = "NOT_FOUND",
    val message: String? = null,
    val booking: BookingDto? = null,
    val eventTitle: String? = null,
    val checkedInAt: String? = null,
    val checkedInBy: String? = null
)

// --------------------------------------------------------------- profile

data class ProfileDto(
    val id: Long = 0,
    val email: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    val role: String = "ROLE_ATTENDEE"
) {
    val displayName: String get() = "$firstName $lastName".trim().ifBlank { email }
    val isOrganizer: Boolean get() = role == "ROLE_ORGANIZER" || role == "ROLE_ADMIN"
}

data class OrganizerStatsDto(
    val totalEvents: Int = 0,
    val publishedEvents: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalTicketsSold: Int = 0
)

// ------------------------------------------------------------- Neon Auth

data class NeonSignUpRequest(
    val email: String,
    val password: String,
    val name: String
)

data class NeonSignInRequest(
    val email: String,
    val password: String
)

data class NeonUserDto(
    val id: String = "",
    val email: String = "",
    val name: String? = null,
    val emailVerified: Boolean = false,
    val image: String? = null
)

/**
 * Returned by both /sign-up/email and /sign-in/email. [token] is the *session*
 * token, not the JWT the Data API wants — that comes from /token.
 */
data class NeonSessionDto(
    val token: String? = null,
    val user: NeonUserDto? = null,
    val redirect: Boolean = false
)

/** The EdDSA-signed JWT the Neon Data API validates. */
data class NeonTokenDto(
    @SerializedName("token") val token: String? = null
)

// -------------------------------------------------------------- payments

data class PaymentInitiateRequest(val bookingId: String)

data class PaymentInitiateResponse(
    /** Where to send the customer to pay. Absent when already paid. */
    val redirectUrl: String? = null,
    val orderTrackingId: String? = null,
    val paymentStatus: String = "PENDING",
    val alreadyPaid: Boolean = false,
    val error: String? = null
)

data class PaymentStatusResponse(
    val paymentStatus: String = "PENDING",
    /** Provider-side wording, useful when a payment failed. */
    val detail: String? = null,
    val started: Boolean = true,
    val error: String? = null
)

// ----------------------------------------------------------------- media

data class MediaUploadResponse(
    val url: String? = null,
    val error: String? = null
)

// ------------------------------------------------------------ M-Pesa

data class MpesaInitiateRequest(
    val bookingId: String,
    /** Typed however the customer types it; the gateway normalises it. */
    val phone: String
)

data class MpesaInitiateResponse(
    val provider: String = "MPESA",
    val checkoutRequestId: String? = null,
    val paymentStatus: String = "PENDING",
    val alreadyPaid: Boolean = false,
    val message: String? = null,
    val error: String? = null
)