package com.eventms.mobile.ui.screens.organizer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.repository.EventRepository
import com.eventms.mobile.data.repository.ScannerRepository
import com.eventms.mobile.domain.ScanOutcome
import com.eventms.mobile.ui.navigation.Routes
import com.eventms.mobile.ui.navigation.longArg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannerUiState(
    val eventTitle: String = "",
    val lastOutcome: ScanOutcome? = null,
    val verifying: Boolean = false,
    val checkedIn: Int = 0,
    val total: Int = 0,
    val pendingSyncs: Int = 0,
    val torchOn: Boolean = false,
    val rosterReady: Boolean = false,
    val manualEntry: String = ""
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val eventRepository: EventRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val eventId: Long = savedStateHandle.longArg(Routes.ARG_EVENT_ID, 0L)

    private val local = MutableStateFlow(ScannerUiState())

    val state: StateFlow<ScannerUiState> = combine(
        local,
        scannerRepository.checkedInCount(eventId),
        scannerRepository.totalCount(eventId),
        scannerRepository.pendingSyncCount
    ) { current, checkedIn, total, pending ->
        current.copy(
            checkedIn = checkedIn,
            total = total,
            pendingSyncs = pending,
            rosterReady = total > 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScannerUiState()
    )

    /**
     * Guards against the analyser firing on the same QR dozens of times a second
     * while it sits in frame.
     */
    private var lastCode: String? = null
    private var lastCodeAt = 0L

    init {
        viewModelScope.launch {
            (eventRepository.getOrganizerEvent(eventId) as? Outcome.Success)?.let {
                local.update { state -> state.copy(eventTitle = it.data.title) }
            }
            // Pull the roster so scanning survives a drop in connectivity.
            scannerRepository.syncRoster(eventId)
            scannerRepository.syncPendingCheckIns()
        }
    }

    fun onBarcodeDetected(raw: String) {
        val now = System.currentTimeMillis()
        if (raw == lastCode && now - lastCodeAt < RESCAN_COOLDOWN_MS) return
        if (local.value.verifying) return

        lastCode = raw
        lastCodeAt = now
        verify(raw)
    }

    fun onManualEntryChange(value: String) = local.update { it.copy(manualEntry = value) }

    fun submitManualEntry() {
        val code = local.value.manualEntry.trim()
        if (code.isBlank()) return
        local.update { it.copy(manualEntry = "") }
        verify(code)
    }

    private fun verify(code: String) {
        local.update { it.copy(verifying = true) }

        viewModelScope.launch {
            val outcome = scannerRepository.verify(code, eventId)
            local.update { it.copy(verifying = false, lastOutcome = outcome) }
        }
    }

    fun dismissOutcome() = local.update { it.copy(lastOutcome = null) }

    fun toggleTorch() = local.update { it.copy(torchOn = !it.torchOn) }

    fun syncNow() {
        viewModelScope.launch {
            scannerRepository.syncPendingCheckIns()
            scannerRepository.syncRoster(eventId)
        }
    }

    private companion object {
        const val RESCAN_COOLDOWN_MS = 2_500L
    }
}
