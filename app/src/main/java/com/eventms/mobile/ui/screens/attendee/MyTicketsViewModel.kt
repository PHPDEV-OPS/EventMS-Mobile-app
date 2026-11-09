package com.eventms.mobile.ui.screens.attendee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.prefs.SessionStore
import com.eventms.mobile.data.repository.TicketRepository
import com.eventms.mobile.domain.Ticket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyTicketsUiState(
    val tickets: List<Ticket> = emptyList(),
    val email: String? = null,
    val refreshing: Boolean = false,
    val error: String? = null
) {
    val upcoming: List<Ticket> get() = tickets.filterNot { it.isUsed }
    val used: List<Ticket> get() = tickets.filter { it.isUsed }
}

@HiltViewModel
class MyTicketsViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val email = sessionStore.session.map { it.email }

    val state: StateFlow<MyTicketsUiState> = combine(
        ticketRepository.myTickets,
        email,
        refreshing,
        error
    ) { tickets, address, isRefreshing, errorMessage ->
        MyTicketsUiState(
            // Only the signed-in person's tickets, even if the cache holds others.
            tickets = tickets.filter {
                address == null || it.email.equals(address, ignoreCase = true)
            },
            email = address,
            refreshing = isRefreshing,
            error = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyTicketsUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return

        viewModelScope.launch {
            val address = sessionStore.current().email
            if (address.isNullOrBlank()) {
                error.value = "Sign in to see your tickets"
                return@launch
            }

            refreshing.value = true
            when (val outcome = ticketRepository.refreshTickets(address)) {
                is Outcome.Success -> error.value = null
                is Outcome.Failure -> error.value = outcome.message
            }
            refreshing.value = false
        }
    }
}
