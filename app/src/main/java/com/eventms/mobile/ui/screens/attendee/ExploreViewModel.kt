package com.eventms.mobile.ui.screens.attendee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.repository.EventRepository
import com.eventms.mobile.domain.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val events: List<Event> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val refreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ExploreUiState> = combine(
        eventRepository.publicEvents,
        query,
        category,
        refreshing,
        error
    ) { events, searchTerm, selectedCategory, isRefreshing, errorMessage ->
        ExploreUiState(
            events = events.filter { it.matches(searchTerm, selectedCategory) },
            categories = events.mapNotNull { it.category?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted(),
            query = searchTerm,
            category = selectedCategory,
            refreshing = isRefreshing,
            error = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExploreUiState()
    )

    fun onQueryChange(value: String) = query.update { value }
    fun onCategoryChange(value: String?) = category.update { value }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true

        viewModelScope.launch {
            when (val outcome = eventRepository.refreshPublicEvents()) {
                is Outcome.Success -> error.value = null
                is Outcome.Failure -> error.value = outcome.message
            }
            refreshing.value = false
        }
    }

    private fun Event.matches(term: String, selectedCategory: String?): Boolean {
        val matchesCategory = selectedCategory == null || category == selectedCategory
        if (!matchesCategory) return false
        if (term.isBlank()) return true

        return title.contains(term, ignoreCase = true) ||
            location.contains(term, ignoreCase = true) ||
            category?.contains(term, ignoreCase = true) == true
    }
}
