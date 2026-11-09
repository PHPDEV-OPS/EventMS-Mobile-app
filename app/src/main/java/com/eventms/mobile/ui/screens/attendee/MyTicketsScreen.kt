package com.eventms.mobile.ui.screens.attendee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Ticket
import com.eventms.mobile.ui.components.EmptyState
import com.eventms.mobile.ui.components.SectionHeader
import com.eventms.mobile.ui.components.StatusPill
import com.eventms.mobile.ui.components.RefreshOnResume
import com.eventms.mobile.ui.theme.Amber60
import com.eventms.mobile.ui.theme.Green60
import com.eventms.mobile.ui.theme.Slate40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTicketsScreen(
    onTicketClick: (String) -> Unit,
    onBrowseEvents: () -> Unit,
    viewModel: MyTicketsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A ticket just paid for should be valid the moment this screen opens.
    RefreshOnResume { viewModel.refresh() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("My tickets", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.email?.let { "Booked with $it" }
                            ?: "Your tickets live here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.tickets.isEmpty() && !state.refreshing) {
                item {
                    EmptyState(
                        title = "No tickets yet",
                        body = "Tickets you buy show up here, ready to scan at the gate.",
                        icon = Icons.Outlined.ConfirmationNumber,
                        actionLabel = "Browse events",
                        onAction = onBrowseEvents
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                items(state.upcoming, key = { it.bookingId }) { ticket ->
                    TicketRow(ticket) { onTicketClick(ticket.bookingId) }
                }
            }

            if (state.used.isNotEmpty()) {
                item { SectionHeader("Already used") }
                items(state.used, key = { it.bookingId }) { ticket ->
                    TicketRow(ticket) { onTicketClick(ticket.bookingId) }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun TicketRow(ticket: Ticket, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    ticket.eventTitle ?: "Event",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = when {
                        ticket.isUsed -> "Used"
                        ticket.isPaid -> "Valid"
                        else -> ticket.paymentStatus.name
                    },
                    color = when {
                        ticket.isUsed -> Slate40
                        ticket.isPaid -> Green60
                        else -> Amber60
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                DateTimes.full(ticket.eventStartDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ticket.eventLocation?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                Text(
                    ticket.ticketNumber ?: ticket.bookingId.take(8).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${ticket.quantity} " + if (ticket.quantity == 1) "ticket" else "tickets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
