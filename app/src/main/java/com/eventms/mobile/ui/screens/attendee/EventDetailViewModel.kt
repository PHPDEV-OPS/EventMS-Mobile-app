package com.eventms.mobile.ui.screens.attendee

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.repository.EventRepository
import com.eventms.mobile.domain.Event
import com.eventms.mobile.ui.navigation.Routes
import com.eventms.mobile.ui.navigation.longArg
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class VenueLocation(val latitude: Double, val longitude: Double, val fromPin: Boolean)

data class EventDetailUiState(
    val event: Event? = null,
    val pastEvents: List<Event> = emptyList(),
    val venue: VenueLocation? = null,
    val venueLookupFailed: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: Long = savedStateHandle.longArg(Routes.ARG_EVENT_ID, 0L)

    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            when (val outcome = eventRepository.getEvent(eventId)) {
                is Outcome.Success -> {
                    val event = outcome.data
                    _state.update { it.copy(event = event, loading = false) }
                    resolveVenue(event)
                    loadOrganizerHistory(event)
                }

                is Outcome.Failure ->
                    _state.update { it.copy(loading = false, error = outcome.message) }
            }
        }
    }

    /**
     * Prefers the organizer's dropped pin; otherwise geocodes the free-text venue
     * so the map still lands somewhere sensible.
     */
    private fun resolveVenue(event: Event) {
        if (event.latitude != null && event.longitude != null) {
            _state.update {
                it.copy(venue = VenueLocation(event.latitude, event.longitude, fromPin = true))
            }
            return
        }

        viewModelScope.launch {
            val resolved = geocode(event.location)
            _state.update {
                it.copy(venue = resolved, venueLookupFailed = resolved == null)
            }
        }
    }

    private suspend fun geocode(address: String): VenueLocation? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || address.isBlank()) return@withContext null

        runCatching {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            results?.firstOrNull()?.let {
                VenueLocation(it.latitude, it.longitude, fromPin = false)
            }
        }.getOrNull()
    }

    /** Everything else this organizer has run, so an attendee can judge them. */
    private fun loadOrganizerHistory(event: Event) {
        viewModelScope.launch {
            val others = eventRepository.otherEventsByOrganizer(event.organizerId, event.id)
            _state.update { it.copy(pastEvents = others) }
        }
    }
}
