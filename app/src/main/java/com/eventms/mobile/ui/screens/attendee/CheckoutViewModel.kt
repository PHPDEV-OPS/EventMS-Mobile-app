package com.eventms.mobile.ui.screens.attendee

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.prefs.SessionStore
import com.eventms.mobile.data.repository.EventRepository
import com.eventms.mobile.data.repository.TicketRepository
import com.eventms.mobile.domain.Event
import com.eventms.mobile.domain.PaymentStatus
import com.eventms.mobile.ui.navigation.Routes
import com.eventms.mobile.ui.navigation.longArg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CheckoutStage {
    FORM,
    CREATING,
    /** Seats held. The booking stays PENDING until the payment clears. */
    AWAITING_PAYMENT,
    /** Opening the checkout, or asking whether it has cleared. */
    WORKING,
    CONFIRMED
}

/** How the customer wants to pay. */
enum class PaymentMethod { MPESA, CARD }

data class CheckoutUiState(
    val event: Event? = null,
    val quantity: Int = 1,
    val email: String = "",
    val phone: String = "",
    val stage: CheckoutStage = CheckoutStage.FORM,
    val bookingId: String? = null,
    /** M-Pesa first: it is how most people here actually pay. */
    val method: PaymentMethod = PaymentMethod.MPESA,
    /** Defaults to the booking phone, but is editable — people pay from another line. */
    val mpesaPhone: String = "",
    /** Emitted once, so the screen can hand off to the payment page. */
    val paymentUrl: String? = null,
    /** Kept so the page can be warmed up before the customer taps Pay. */
    val warmUrl: String? = null,
    /** True between opening the payment page and coming back from it. */
    val awaitingReturn: Boolean = false,
    val notice: String? = null,
    val error: String? = null
) {
    val total: Double get() = (event?.ticketPrice ?: 0.0) * quantity
    val isFree: Boolean get() = (event?.ticketPrice ?: 0.0) <= 0.0
    val maxQuantity: Int
        get() = minOf(MAX_PER_BOOKING, (event?.availableTickets ?: MAX_PER_BOOKING))
            .coerceAtLeast(1)

    val busy: Boolean
        get() = stage == CheckoutStage.CREATING || stage == CheckoutStage.WORKING

    val canSubmit: Boolean
        get() = stage == CheckoutStage.FORM &&
            email.contains("@") &&
            phone.filter(Char::isDigit).length >= 9 &&
            quantity in 1..maxQuantity

    companion object {
        /** api_create_booking rejects more than ten tickets in one booking. */
        const val MAX_PER_BOOKING = 10
    }
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val ticketRepository: TicketRepository,
    private val sessionStore: SessionStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: Long = savedStateHandle.longArg(Routes.ARG_EVENT_ID, 0L)

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The signed-in address is the one the ticket belongs to.
            val session = sessionStore.current()
            _state.update { it.copy(email = session.email.orEmpty()) }

            when (val outcome = eventRepository.getEvent(eventId)) {
                is Outcome.Success -> _state.update { it.copy(event = outcome.data) }
                is Outcome.Failure -> _state.update { it.copy(error = outcome.message) }
            }
        }
    }

    fun onQuantityChange(value: Int) = _state.update {
        it.copy(quantity = value.coerceIn(1, it.maxQuantity), error = null)
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, error = null) }

    /** Holds the seats, then goes straight on to the payment step. */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(stage = CheckoutStage.CREATING, error = null) }

        viewModelScope.launch {
            when (val booking = ticketRepository.createBooking(
                eventId = eventId,
                email = current.email,
                phone = current.phone,
                quantity = current.quantity
            )) {
                is Outcome.Failure -> _state.update {
                    it.copy(stage = CheckoutStage.FORM, error = booking.message)
                }

                is Outcome.Success -> _state.update {
                    it.copy(
                        stage = CheckoutStage.AWAITING_PAYMENT,
                        bookingId = booking.data.id,
                        // Seed the M-Pesa number from the contact number given.
                        mpesaPhone = it.mpesaPhone.ifBlank { it.phone }
                    )
                }
            }
        }
    }

    fun onMethodChange(method: PaymentMethod) =
        _state.update { it.copy(method = method, error = null, notice = null) }

    fun onMpesaPhoneChange(value: String) =
        _state.update { it.copy(mpesaPhone = value, error = null) }

    /**
     * Starts the payment with whichever provider was chosen. A free event comes
     * back settled either way, with nothing to open and no prompt to answer.
     */
    fun pay() {
        val current = _state.value
        val bookingId = current.bookingId ?: return
        if (current.stage == CheckoutStage.WORKING) return

        _state.update { it.copy(stage = CheckoutStage.WORKING, error = null, notice = null) }

        viewModelScope.launch {
            val outcome = if (current.method == PaymentMethod.MPESA) {
                ticketRepository.startMpesaPayment(bookingId, current.mpesaPhone)
            } else {
                ticketRepository.startPayment(bookingId)
            }

            when (outcome) {
                is Outcome.Success -> {
                    val result = outcome.data
                    when {
                        // Nothing to pay, or already settled.
                        result == null ->
                            _state.update { it.copy(stage = CheckoutStage.CONFIRMED) }

                        // M-Pesa answers with a message; the prompt is on the phone.
                        current.method == PaymentMethod.MPESA -> _state.update {
                            it.copy(stage = CheckoutStage.AWAITING_PAYMENT, notice = result)
                        }

                        else -> _state.update {
                            it.copy(
                                stage = CheckoutStage.AWAITING_PAYMENT,
                                paymentUrl = result,
                                warmUrl = result
                            )
                        }
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(stage = CheckoutStage.AWAITING_PAYMENT, error = outcome.message)
                }
            }
        }
    }

    /** The payment page is now on screen; remember to check on the way back. */
    fun onPaymentUrlOpened() =
        _state.update { it.copy(paymentUrl = null, awaitingReturn = true) }

    /**
     * Called when the screen comes back to the foreground.
     *
     * Closing the payment tab drops the customer straight back here, so asking
     * the gateway at that moment usually turns the ticket valid without them
     * touching anything. It stays a no-op until a payment has actually started.
     */
    fun onReturnedFromPayment() {
        if (!_state.value.awaitingReturn) return
        _state.update { it.copy(awaitingReturn = false) }
        checkPayment()
    }

    /**
     * Asks the gateway whether the payment cleared. The app has no permission to
     * settle a booking itself, so this is the only way it becomes valid.
     */
    fun checkPayment() {
        val bookingId = _state.value.bookingId ?: return
        if (_state.value.stage == CheckoutStage.WORKING) return

        _state.update { it.copy(stage = CheckoutStage.WORKING, error = null, notice = null) }

        viewModelScope.launch {
            when (val outcome = ticketRepository.checkPaymentStatus(bookingId)) {
                is Outcome.Success -> when (outcome.data) {
                    PaymentStatus.PAID ->
                        _state.update { it.copy(stage = CheckoutStage.CONFIRMED) }

                    PaymentStatus.FAILED -> _state.update {
                        it.copy(
                            stage = CheckoutStage.AWAITING_PAYMENT,
                            error = "That payment did not go through. You can try again."
                        )
                    }

                    else -> _state.update {
                        it.copy(
                            stage = CheckoutStage.AWAITING_PAYMENT,
                            notice = "No payment recorded yet. If you have just paid, " +
                                "give it a moment and check again."
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(stage = CheckoutStage.AWAITING_PAYMENT, error = outcome.message)
                }
            }
        }
    }
}
