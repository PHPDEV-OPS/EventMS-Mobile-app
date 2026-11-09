package com.eventms.mobile.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.prefs.SessionStore
import com.eventms.mobile.data.repository.AuthRepository
import com.eventms.mobile.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val displayName: String? = null,
    val email: String? = null,
    val isOrganizer: Boolean = false,
    val working: Boolean = false,
    val signedOut: Boolean = false,
    val error: String? = null
) {
    val initials: String
        get() = (displayName ?: email.orEmpty())
            .split(" ", ".", "@")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val ticketRepository: TicketRepository,
    sessionStore: SessionStore
) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val signedOut = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<AccountUiState> = combine(
        sessionStore.session,
        working,
        signedOut,
        error
    ) { session, isWorking, isSignedOut, errorMessage ->
        AccountUiState(
            displayName = session.displayName,
            email = session.email,
            isOrganizer = session.isOrganizer,
            working = isWorking,
            signedOut = isSignedOut,
            error = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState()
    )

    fun becomeOrganizer() {
        if (working.value) return
        working.value = true

        viewModelScope.launch {
            when (val outcome = authRepository.becomeOrganizer()) {
                is Outcome.Success -> error.value = null
                is Outcome.Failure -> error.value = outcome.message
            }
            working.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Cached tickets belong to the account that just left the device.
            ticketRepository.clearCachedTickets()
            authRepository.signOut()
            signedOut.value = true
        }
    }
}
