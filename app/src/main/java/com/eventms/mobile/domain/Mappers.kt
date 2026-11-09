package com.eventms.mobile.domain

import com.eventms.mobile.data.local.CachedEventEntity
import com.eventms.mobile.data.local.CachedTicketEntity
import com.eventms.mobile.data.local.RosterEntryEntity
import com.eventms.mobile.data.remote.dto.BookingDto
import com.eventms.mobile.data.remote.dto.EventDto
import com.eventms.mobile.data.remote.dto.TicketVerificationResponseDto

// ---------------------------------------------------------------- events

fun EventDto.toDomain() = Event(
    id = id,
    title = title,
    description = description,
    location = location,
    latitude = latitude,
    longitude = longitude,
    startDate = DateTimes.parse(startDate),
    endDate = DateTimes.parse(endDate),
    ticketPrice = ticketPrice.toDouble(),
    totalTickets = totalTickets,
    availableTickets = availableTickets,
    status = EventStatus.from(status),
    organizerId = organizerId,
    imageUrl = imageUrl,
    category = category
)

fun EventDto.toEntity(ownedByOrganizer: Boolean) = CachedEventEntity(
    id = id,
    title = title,
    description = description,
    location = location,
    latitude = latitude,
    longitude = longitude,
    startDate = startDate,
    endDate = endDate,
    ticketPrice = ticketPrice.toDouble(),
    totalTickets = totalTickets,
    availableTickets = availableTickets,
    status = status,
    organizerId = organizerId,
    imageUrl = imageUrl,
    category = category,
    ownedByOrganizer = ownedByOrganizer
)

fun CachedEventEntity.toDomain() = Event(
    id = id,
    title = title,
    description = description,
    location = location,
    latitude = latitude,
    longitude = longitude,
    startDate = DateTimes.parse(startDate),
    endDate = DateTimes.parse(endDate),
    ticketPrice = ticketPrice,
    totalTickets = totalTickets,
    availableTickets = availableTickets,
    status = EventStatus.from(status),
    organizerId = organizerId,
    imageUrl = imageUrl,
    category = category
)

// --------------------------------------------------------------- tickets

fun BookingDto.toEntity(event: EventDto?) = CachedTicketEntity(
    bookingId = id,
    eventId = eventId,
    email = email,
    phone = phone,
    quantity = quantity,
    status = status,
    paymentStatus = paymentStatus,
    totalAmount = totalAmount.toDouble(),
    ticketNumber = ticketNumber,
    confirmedAt = confirmedAt,
    checkedInAt = checkedInAt,
    createdAt = createdAt,
    eventTitle = event?.title,
    eventLocation = event?.location,
    eventStartDate = event?.startDate
)

fun CachedTicketEntity.toDomain() = Ticket(
    bookingId = bookingId,
    eventId = eventId,
    email = email,
    phone = phone,
    quantity = quantity,
    status = BookingStatus.from(status),
    paymentStatus = PaymentStatus.from(paymentStatus),
    totalAmount = totalAmount,
    ticketNumber = ticketNumber,
    confirmedAt = DateTimes.parse(confirmedAt),
    checkedInAt = DateTimes.parse(checkedInAt),
    createdAt = DateTimes.parse(createdAt),
    eventTitle = eventTitle,
    eventLocation = eventLocation,
    eventStartDate = DateTimes.parse(eventStartDate)
)

fun BookingDto.toTicket(event: Event?) = Ticket(
    bookingId = id,
    eventId = eventId,
    email = email,
    phone = phone,
    quantity = quantity,
    status = BookingStatus.from(status),
    paymentStatus = PaymentStatus.from(paymentStatus),
    totalAmount = totalAmount.toDouble(),
    ticketNumber = ticketNumber,
    confirmedAt = DateTimes.parse(confirmedAt),
    checkedInAt = DateTimes.parse(checkedInAt),
    createdAt = DateTimes.parse(createdAt),
    eventTitle = event?.title,
    eventLocation = event?.location,
    eventStartDate = event?.startDate
)

// ---------------------------------------------------------------- roster

fun BookingDto.toRosterEntry() = RosterEntryEntity(
    bookingId = id,
    eventId = eventId,
    ticketNumber = ticketNumber,
    email = email,
    quantity = quantity,
    status = status,
    paymentStatus = paymentStatus,
    checkedInAt = checkedInAt
)

fun RosterEntryEntity.toAttendee() = Attendee(
    bookingId = bookingId,
    ticketNumber = ticketNumber,
    email = email,
    quantity = quantity,
    paymentStatus = PaymentStatus.from(paymentStatus),
    checkedInAt = DateTimes.parse(checkedInAt)
)

fun BookingDto.toAttendee() = Attendee(
    bookingId = id,
    ticketNumber = ticketNumber,
    email = email,
    quantity = quantity,
    paymentStatus = PaymentStatus.from(paymentStatus),
    checkedInAt = DateTimes.parse(checkedInAt)
)

// ------------------------------------------------------------- scanning

fun TicketVerificationResponseDto.toOutcome() = ScanOutcome(
    result = ScanResult.from(result),
    message = message ?: "Ticket checked",
    ticketNumber = booking?.ticketNumber,
    attendeeEmail = booking?.email,
    quantity = booking?.quantity,
    eventTitle = eventTitle,
    checkedInAt = DateTimes.parse(checkedInAt)
)
