package com.eventms.mobile.ui.screens.attendee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.ui.components.EmptyState
import com.eventms.mobile.ui.components.EventCard
import com.eventms.mobile.ui.components.ErrorState
import com.eventms.mobile.ui.components.RefreshOnResume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onEventClick: (Long) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reloads whenever the tab is shown, so a newly published event appears
    // without the user having to pull down.
    RefreshOnResume { viewModel.refresh() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text("Discover events", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Find something worth going to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search events or venues") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.categories.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.category == null,
                                onClick = { viewModel.onCategoryChange(null) },
                                label = { Text("All") }
                            )
                        }
                        items(state.categories) { category ->
                            FilterChip(
                                selected = state.category == category,
                                onClick = { viewModel.onCategoryChange(category) },
                                label = { Text(category) }
                            )
                        }
                    }
                }
            }

            when {
                state.error != null && state.events.isEmpty() -> item {
                    ErrorState(state.error!!, onRetry = viewModel::refresh)
                }

                state.events.isEmpty() && !state.refreshing -> item {
                    EmptyState(
                        title = if (state.query.isBlank()) "No events yet" else "Nothing matched",
                        body = if (state.query.isBlank()) {
                            "Published events from organizers will show up here."
                        } else {
                            "Try a different search or clear the filters."
                        }
                    )
                }

                else -> {
                    // A failed refresh over a populated cache is a banner, not a takeover.
                    state.error?.let { message ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Showing saved events — $message",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(state.events, key = { it.id }) { event ->
                        EventCard(event = event, onClick = { onEventClick(event.id) })
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}
