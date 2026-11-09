package com.eventms.mobile.ui.screens.attendee

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.core.TicketPdf
import com.eventms.mobile.data.repository.TicketRepository
import com.eventms.mobile.domain.PaymentStatus
import com.eventms.mobile.domain.Ticket
import com.eventms.mobile.ui.navigation.Routes
import com.eventms.mobile.ui.navigation.stringArg
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TicketDetailUiState(
    val ticket: Ticket? = null,
    val downloading: Boolean = false,
    val working: Boolean = false,
    /** Prefilled from the booking, but editable — people pay from another line. */
    val mpesaPhone: String = "",
    /** Emitted once, so the screen can hand off to the payment page. */
    val paymentUrl: String? = null,
    /** Kept so the page can be warmed up before it is opened. */
    val warmUrl: String? = null,
    /** True between opening the payment page and coming back from it. */
    val awaitingReturn: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookingId: String = savedStateHandle.stringArg(Routes.ARG_BOOKING_ID)

    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ticketRepository.ticket(bookingId).collect { ticket ->
                _state.update {
                    it.copy(
                        ticket = ticket,
                        // Seed the M-Pesa field once, then leave the user's edit alone.
                        mpesaPhone = it.mpesaPhone.ifBlank { ticket?.phone.orEmpty() }
                    )
                }
            }
        }
        // Pick up a payment or check-in that happened elsewhere.
        viewModelScope.launch { ticketRepository.refreshBooking(bookingId) }
    }

    fun onMpesaPhoneChange(value: String) = _state.update { it.copy(mpesaPhone = value) }

    /**
     * Resumes an unfinished payment on the phone — the booking was held, not
     * abandoned, so there is nothing to re-book.
     */
    fun payWithMpesa() {
        if (_state.value.working) return
        val phone = _state.value.mpesaPhone.ifBlank { _state.value.ticket?.phone.orEmpty() }

        _state.update { it.copy(working = true, message = null) }

        viewModelScope.launch {
            when (val outcome = ticketRepository.startMpesaPayment(bookingId, phone)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        working = false,
                        message = outcome.data ?: "This ticket is already paid for"
                    )
                }

                is Outcome.Failure ->
                    _state.update { it.copy(working = false, message = outcome.message) }
            }
        }
    }

    /** Resumes an unfinished payment in the browser instead. */
    fun payWithCard() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }

        viewModelScope.launch {
            when (val outcome = ticketRepository.startPayment(bookingId)) {
                is Outcome.Success -> {
                    val url = outcome.data
                    _state.update {
                        it.copy(
                            working = false,
                            paymentUrl = url,
                            warmUrl = url,
                            message = if (url == null) "This ticket is already paid for" else null
                        )
                    }
                }

                is Outcome.Failure ->
                    _state.update { it.copy(working = false, message = outcome.message) }
            }
        }
    }

    /** The payment page is now on screen; remember to check on the way back. */
    fun onPaymentUrlOpened() =
        _state.update { it.copy(paymentUrl = null, awaitingReturn = true) }

    /**
     * Called when the screen returns to the foreground. Closing the payment tab
     * lands the customer back here, so the ticket usually turns valid on its own.
     */
    fun onReturnedFromPayment() {
        if (!_state.value.awaitingReturn) return
        _state.update { it.copy(awaitingReturn = false) }
        checkPayment()
    }

    fun checkPayment() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }

        viewModelScope.launch {
            val message = when (val outcome = ticketRepository.checkPaymentStatus(bookingId)) {
                is Outcome.Success -> when (outcome.data) {
                    PaymentStatus.PAID -> "Payment confirmed — your ticket is ready"
                    PaymentStatus.FAILED -> "That payment did not go through"
                    else -> "No payment recorded yet. Try again in a moment."
                }

                is Outcome.Failure -> outcome.message
            }
            _state.update { it.copy(working = false, message = message) }
        }
    }

    /**
     * Saves the ticket as a PDF.
     *
     * Refuses while the booking is unpaid: an unpaid ticket would be turned away
     * at the gate, and handing out a printable copy of one invites trouble. The
     * gate check is enforced server-side too — this is the polite front door.
     */
    fun downloadPdf() {
        val ticket = _state.value.ticket ?: return
        if (_state.value.downloading) return

        if (!ticket.isPaid) {
            _state.update {
                it.copy(message = "Complete the payment first to download this ticket")
            }
            return
        }

        _state.update { it.copy(downloading = true) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { TicketPdf.save(context, ticket) }

            _state.update {
                it.copy(
                    downloading = false,
                    message = result.fold(
                        onSuccess = { "Ticket saved to Downloads" },
                        onFailure = { error ->
                            "Could not save the ticket: ${error.message ?: "unknown error"}"
                        }
                    )
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
