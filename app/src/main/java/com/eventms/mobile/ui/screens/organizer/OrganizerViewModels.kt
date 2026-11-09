package com.eventms.mobile.ui.screens.organizer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.prefs.SessionStore
import com.eventms.mobile.data.repository.EventForm
import com.eventms.mobile.data.repository.EventRepository
import com.eventms.mobile.data.repository.MediaRepository
import com.eventms.mobile.data.repository.PlaceSuggestion
import com.eventms.mobile.data.repository.PlacesRepository
import com.eventms.mobile.data.repository.ScannerRepository
import com.eventms.mobile.domain.Attendee
import com.eventms.mobile.domain.Event
import com.eventms.mobile.domain.EventStatus
import com.eventms.mobile.domain.OrganizerStats
import com.eventms.mobile.ui.navigation.Routes
import com.eventms.mobile.ui.navigation.longArg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

// ------------------------------------------------------------- dashboard

data class OrganizerDashboardUiState(
    val stats: OrganizerStats = OrganizerStats(),
    val events: List<Event> = emptyList(),
    val organizerName: String? = null,
    val pendingScanSyncs: Int = 0,
    val refreshing: Boolean = false,
    val error: String? = null
) {
    val upcoming: List<Event>
        get() = events.filter { !it.isPast && it.status == EventStatus.PUBLISHED }
    val drafts: List<Event> get() = events.filter { it.status == EventStatus.DRAFT }
}

@HiltViewModel
class OrganizerDashboardViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val scannerRepository: ScannerRepository,
    sessionStore: SessionStore
) : ViewModel() {

    private val stats = MutableStateFlow(OrganizerStats())
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<OrganizerDashboardUiState> = combine(
        eventRepository.organizerEvents,
        stats,
        sessionStore.session.map { it.displayName },
        scannerRepository.pendingSyncCount,
        combine(refreshing, error) { isRefreshing, message -> isRefreshing to message }
    ) { events, currentStats, name, pending, flags ->
        val (isRefreshing, message) = flags
        OrganizerDashboardUiState(
            stats = currentStats,
            events = events,
            organizerName = name,
            pendingScanSyncs = pending,
            refreshing = isRefreshing,
            error = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OrganizerDashboardUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true

        viewModelScope.launch {
            when (val outcome = eventRepository.refreshOrganizerEvents()) {
                is Outcome.Success -> error.value = null
                is Outcome.Failure -> error.value = outcome.message
            }

            (eventRepository.getStats() as? Outcome.Success)?.let { stats.value = it.data }

            // Opportunistic: push anything scanned while the venue had no signal.
            scannerRepository.syncPendingCheckIns()

            refreshing.value = false
        }
    }
}

// ---------------------------------------------------------- event editor

data class EventEditorUiState(
    val form: EventForm = EventForm(),
    val isNew: Boolean = true,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val uploadingImage: Boolean = false,
    val deleted: Boolean = false,
    val savedEventId: Long? = null,
    /** Venues matching what has been typed so far. */
    val venueSuggestions: List<PlaceSuggestion> = emptyList(),
    val searchingVenues: Boolean = false,
    /** True once a suggestion is picked, so the coordinates are Google's own. */
    val venueConfirmed: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val mediaRepository: MediaRepository,
    private val placesRepository: PlacesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Restarted on each keystroke so only the last query is in flight. */
    private var venueSearchJob: Job? = null

    private val eventId: Long = savedStateHandle.longArg(Routes.ARG_EVENT_ID, Routes.NEW_EVENT_ID)

    private val _state = MutableStateFlow(
        EventEditorUiState(
            isNew = eventId == Routes.NEW_EVENT_ID,
            loading = eventId != Routes.NEW_EVENT_ID,
            form = EventForm(
                startDate = LocalDateTime.now().plusDays(7).withHour(18).withMinute(0),
                endDate = LocalDateTime.now().plusDays(7).withHour(22).withMinute(0)
            )
        )
    )
    val state: StateFlow<EventEditorUiState> = _state.asStateFlow()

    init {
        if (eventId != Routes.NEW_EVENT_ID) {
            viewModelScope.launch {
                when (val outcome = eventRepository.getOrganizerEvent(eventId)) {
                    is Outcome.Success -> _state.update {
                        it.copy(form = EventForm.from(outcome.data), loading = false)
                    }
                    is Outcome.Failure -> _state.update {
                        it.copy(loading = false, error = outcome.message)
                    }
                }
            }
        }
    }

    fun update(transform: (EventForm) -> EventForm) =
        _state.update { it.copy(form = transform(it.form), error = null) }

    /**
     * Typing in the venue field.
     *
     * Any edit invalidates a previously picked place — the coordinates on the
     * form belonged to that place, not to whatever is being typed now — so they
     * are cleared until a suggestion is chosen again.
     */
    fun onVenueQueryChange(query: String) {
        _state.update {
            it.copy(
                form = it.form.copy(location = query, latitude = null, longitude = null),
                venueConfirmed = false,
                error = null
            )
        }

        venueSearchJob?.cancel()

        if (query.trim().length < 3) {
            _state.update { it.copy(venueSuggestions = emptyList(), searchingVenues = false) }
            return
        }

        venueSearchJob = viewModelScope.launch {
            // Wait for a pause in typing: one request per word beats one per letter.
            delay(VENUE_SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searchingVenues = true) }

            val suggestions = (placesRepository.search(query) as? Outcome.Success)?.data.orEmpty()
            _state.update { it.copy(venueSuggestions = suggestions, searchingVenues = false) }
        }
    }

    /** Locks in a real place, along with the coordinates Google holds for it. */
    fun onVenueSelected(suggestion: PlaceSuggestion) {
        venueSearchJob?.cancel()
        _state.update {
            it.copy(
                form = it.form.copy(location = suggestion.fullText),
                venueSuggestions = emptyList(),
                venueConfirmed = true
            )
        }

        viewModelScope.launch {
            when (val resolved = placesRepository.resolve(suggestion.placeId)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        form = it.form.copy(
                            location = resolved.data.displayLocation,
                            latitude = resolved.data.latitude,
                            longitude = resolved.data.longitude
                        )
                    )
                }

                // The name is still right; the map falls back to geocoding it.
                is Outcome.Failure -> Unit
            }
        }
    }

    fun dismissVenueSuggestions() =
        _state.update { it.copy(venueSuggestions = emptyList()) }

    /**
     * Uploads a cover picked from the device and stores the resulting URL on
     * the form. The image is hosted rather than embedded so the web app renders
     * the same picture.
     */
    fun onCoverImagePicked(uri: Uri) {
        if (_state.value.uploadingImage) return
        _state.update { it.copy(uploadingImage = true, error = null) }

        viewModelScope.launch {
            when (val outcome = mediaRepository.uploadCoverImage(uri)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        uploadingImage = false,
                        form = it.form.copy(imageUrl = outcome.data)
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(uploadingImage = false, error = outcome.message)
                }
            }
        }
    }

    /** Drops the cover; the category's stock image stands in from then on. */
    fun onCoverImageCleared() =
        _state.update { it.copy(form = it.form.copy(imageUrl = "")) }

    fun save() {
        val form = _state.value.form

        form.validate()?.let { problem ->
            _state.update { it.copy(error = problem) }
            return
        }

        _state.update { it.copy(saving = true, error = null) }

        viewModelScope.launch {
            val outcome = if (_state.value.isNew) {
                eventRepository.createEvent(form)
            } else {
                eventRepository.updateEvent(eventId, form)
            }

            when (outcome) {
                is Outcome.Success -> _state.update {
                    it.copy(saving = false, savedEventId = outcome.data.id)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(saving = false, error = outcome.message)
                }
            }
        }
    }

    private companion object {
        /** Long enough to skip mid-word keystrokes, short enough to feel live. */
        const val VENUE_SEARCH_DEBOUNCE_MS = 300L
    }

    fun delete() {
        if (_state.value.isNew) return
        _state.update { it.copy(saving = true, error = null) }

        viewModelScope.launch {
            when (val outcome = eventRepository.deleteEvent(eventId)) {
                is Outcome.Success -> _state.update { it.copy(saving = false, deleted = true) }
                is Outcome.Failure -> _state.update {
                    it.copy(saving = false, error = outcome.message)
                }
            }
        }
    }
}

// -------------------------------------------------------------- attendees

data class AttendeesUiState(
    val attendees: List<Attendee> = emptyList(),
    val eventTitle: String = "",
    val checkedIn: Int = 0,
    val total: Int = 0,
    val syncing: Boolean = false,
    val error: String? = null
) {
    val ticketsIssued: Int get() = attendees.sumOf { it.quantity }
    val paidCount: Int get() = attendees.count { it.paymentStatus.name == "PAID" }
}

@HiltViewModel
class AttendeesViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val eventRepository: EventRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: Long = savedStateHandle.longArg(Routes.ARG_EVENT_ID, 0L)

    private val syncing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val eventTitle = MutableStateFlow("")

    val state: StateFlow<AttendeesUiState> = combine(
        scannerRepository.attendees(eventId),
        scannerRepository.checkedInCount(eventId),
        scannerRepository.totalCount(eventId),
        eventTitle,
        combine(syncing, error) { isSyncing, message -> isSyncing to message }
    ) { attendees, checkedIn, total, title, flags ->
        val (isSyncing, message) = flags
        AttendeesUiState(
            attendees = attendees,
            eventTitle = title,
            checkedIn = checkedIn,
            total = total,
            syncing = isSyncing,
            error = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AttendeesUiState()
    )

    init {
        viewModelScope.launch {
            (eventRepository.getOrganizerEvent(eventId) as? Outcome.Success)?.let {
                eventTitle.value = it.data.title
            }
        }
        sync()
    }

    /** Re-downloads the roster; also what makes offline scanning possible. */
    fun sync() {
        if (syncing.value) return
        syncing.value = true

        viewModelScope.launch {
            scannerRepository.syncPendingCheckIns()
            when (val outcome = scannerRepository.syncRoster(eventId)) {
                is Outcome.Success -> error.value = null
                is Outcome.Failure -> error.value = outcome.message
            }
            syncing.value = false
        }
    }
}

// ---------------------------------------------------------- scan picker

data class ScanPickerUiState(
    val events: List<Event> = emptyList(),
    val pendingSyncs: Int = 0,
    val loading: Boolean = true
)

@HiltViewModel
class ScanPickerViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val scannerRepository: ScannerRepository
) : ViewModel() {

    val state: StateFlow<ScanPickerUiState> = combine(
        eventRepository.organizerEvents,
        scannerRepository.pendingSyncCount
    ) { events, pending ->
        ScanPickerUiState(
            // Only events that can actually have people at the door.
            events = events.filter { it.status == EventStatus.PUBLISHED },
            pendingSyncs = pending,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScanPickerUiState()
    )

    init {
        refresh()
    }

    /** Also flushes anything scanned while the venue had no signal. */
    fun refresh() {
        viewModelScope.launch {
            eventRepository.refreshOrganizerEvents()
            scannerRepository.syncPendingCheckIns()
        }
    }
}
