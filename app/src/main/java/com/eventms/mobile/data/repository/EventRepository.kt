package com.eventms.mobile.data.repository

import com.eventms.mobile.core.Outcome
import com.eventms.mobile.core.outcomeOf
import com.eventms.mobile.data.local.EventDao
import com.eventms.mobile.data.prefs.SessionStore
import com.eventms.mobile.data.remote.NeonDataApi
import com.eventms.mobile.data.remote.dto.EventWriteDto
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Event
import com.eventms.mobile.domain.EventCategories
import com.eventms.mobile.domain.EventStatus
import com.eventms.mobile.domain.OrganizerStats
import com.eventms.mobile.domain.toDomain
import com.eventms.mobile.domain.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Events, cache-first.
 *
 * Room is the source of truth the UI observes; the Data API refreshes it. That
 * keeps the event list on screen when the network drops, which matters most at
 * a venue.
 */
@Singleton
class EventRepository @Inject constructor(
    private val api: NeonDataApi,
    private val eventDao: EventDao,
    private val sessionStore: SessionStore
) {

    val publicEvents: Flow<List<Event>> =
        eventDao.observePublicEvents().map { rows -> rows.map { it.toDomain() } }

    val organizerEvents: Flow<List<Event>> =
        eventDao.observeOrganizerEvents().map { rows -> rows.map { it.toDomain() } }

    suspend fun refreshPublicEvents(): Outcome<Unit> = outcomeOf {
        val events = api.getPublishedEvents()
        eventDao.clear(ownedByOrganizer = false)
        eventDao.upsertAll(events.map { it.toEntity(ownedByOrganizer = false) })
    }

    suspend fun refreshOrganizerEvents(): Outcome<Unit> = outcomeOf {
        val organizerId = requireOrganizerId()
        val events = api.getOrganizerEvents(organizerId = "eq.$organizerId")
        eventDao.clear(ownedByOrganizer = true)
        eventDao.upsertAll(events.map { it.toEntity(ownedByOrganizer = true) })
    }

    /** Reads through to the cache so an event page opens instantly, then refreshes. */
    suspend fun getEvent(id: Long): Outcome<Event> {
        val cached = eventDao.findById(id)?.toDomain()

        return when (val fresh = outcomeOf { api.getEventById(id = "eq.$id").firstOrNull() }) {
            is Outcome.Success -> {
                val event = fresh.data
                when {
                    event != null -> {
                        eventDao.upsert(event.toEntity(ownedByOrganizer = false))
                        Outcome.Success(event.toDomain())
                    }
                    cached != null -> Outcome.Success(cached)
                    else -> Outcome.Failure("That event is no longer available")
                }
            }

            is Outcome.Failure ->
                if (cached != null) Outcome.Success(cached) else fresh
        }
    }

    /** Same row, but cached as organizer-owned so drafts survive a refresh. */
    suspend fun getOrganizerEvent(id: Long): Outcome<Event> = outcomeOf {
        val event = api.getEventById(id = "eq.$id").firstOrNull()
            ?: throw IllegalStateException("Event not found")
        eventDao.upsert(event.toEntity(ownedByOrganizer = true))
        event.toDomain()
    }

    /**
     * Other events by the same organizer, newest first, so an attendee can see
     * what this organizer has run before. Falls back to the cache when offline.
     */
    suspend fun otherEventsByOrganizer(organizerId: Long, excludeEventId: Long): List<Event> {
        runCatching {
            val events = api.getEventsByOrganizer(organizerId = "eq.$organizerId")
            eventDao.upsertAll(events.map { it.toEntity(ownedByOrganizer = false) })
        }
        return eventDao.findByOrganizer(organizerId, excludeEventId).map { it.toDomain() }
    }

    suspend fun createEvent(form: EventForm): Outcome<Event> = outcomeOf {
        val organizerId = requireOrganizerId()
        val created = api.createEvent(form.toWriteDto(organizerId, isNew = true)).first()
        eventDao.upsert(created.toEntity(ownedByOrganizer = true))
        created.toDomain()
    }

    suspend fun updateEvent(id: Long, form: EventForm): Outcome<Event> = outcomeOf {
        val organizerId = requireOrganizerId()
        val updated = api.updateEvent(
            id = "eq.$id",
            event = form.toWriteDto(organizerId, isNew = false)
        ).first()
        eventDao.upsert(updated.toEntity(ownedByOrganizer = true))
        updated.toDomain()
    }

    suspend fun deleteEvent(id: Long): Outcome<Unit> = outcomeOf {
        api.deleteEvent(id = "eq.$id")
        eventDao.deleteById(id)
    }

    suspend fun getStats(): Outcome<OrganizerStats> = outcomeOf {
        val raw = api.getOrganizerStats()
        OrganizerStats(
            totalEvents = raw.totalEvents,
            publishedEvents = raw.publishedEvents,
            totalRevenue = raw.totalRevenue,
            totalTicketsSold = raw.totalTicketsSold
        )
    }

    /** Row-level security keys off this, so it has to be present. */
    private suspend fun requireOrganizerId(): Long =
        sessionStore.current().userId
            ?: throw IllegalStateException("Sign in as an organizer first")
}

/** What the event editor collects, before it becomes an API request. */
data class EventForm(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val ticketPrice: String = "",
    val totalTickets: String = "",
    val status: EventStatus = EventStatus.DRAFT,
    val imageUrl: String = "",
    /** Always one of [EventCategories.ALL]; never free text. */
    val category: String = EventCategories.ALL.last()
) {
    /**
     * `available_tickets` and `organizer_id` go out only when creating. On an
     * update they would overwrite the count existing bookings have drawn down,
     * and re-assert ownership that row-level security already enforces — Gson
     * omits nulls, so leaving them null keeps them out of the PATCH body.
     *
     * The optional text fields are sent as empty strings for the opposite
     * reason: null would be dropped from the body, so clearing a description or
     * cover image in the editor would silently fail to save.
     */
    fun toWriteDto(organizerId: Long, isNew: Boolean): EventWriteDto {
        val capacity = totalTickets.trim().toIntOrNull() ?: 0
        return EventWriteDto(
            title = title.trim(),
            description = description.trim(),
            location = location.trim(),
            latitude = latitude,
            longitude = longitude,
            startDate = DateTimes.toApi(requireNotNull(startDate) { "Start date is required" }),
            endDate = DateTimes.toApi(requireNotNull(endDate) { "End date is required" }),
            ticketPrice = BigDecimal(ticketPrice.trim().ifBlank { "0" }),
            totalTickets = capacity,
            availableTickets = if (isNew) capacity else null,
            status = status.name,
            organizerId = if (isNew) organizerId else null,
            imageUrl = imageUrl.trim(),
            category = EventCategories.normalise(category)
        )
    }

    /** Client-side validation mirroring the checks in the SQL functions. */
    fun validate(): String? = when {
        title.isBlank() -> "Give the event a title"
        location.isBlank() -> "Where is it happening?"
        startDate == null -> "Pick a start date"
        endDate == null -> "Pick an end date"
        !endDate.isAfter(startDate) -> "The end must come after the start"
        (totalTickets.toIntOrNull() ?: 0) < 1 -> "There must be at least one ticket"
        ticketPrice.isNotBlank() && ticketPrice.toDoubleOrNull() == null -> "Price is not a number"
        (ticketPrice.toDoubleOrNull() ?: 0.0) < 0 -> "Price cannot be negative"
        else -> null
    }

    companion object {
        fun from(event: Event) = EventForm(
            title = event.title,
            description = event.description.orEmpty(),
            location = event.location,
            latitude = event.latitude,
            longitude = event.longitude,
            startDate = event.startDate,
            endDate = event.endDate,
            ticketPrice = if (event.ticketPrice > 0) {
                event.ticketPrice.toBigDecimal().stripTrailingZeros().toPlainString()
            } else {
                "0"
            },
            totalTickets = event.totalTickets.toString(),
            status = event.status,
            imageUrl = event.imageUrl.orEmpty(),
            category = EventCategories.normalise(event.category)
        )
    }
}
