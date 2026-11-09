package com.eventms.mobile.ui.screens.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventms.mobile.ui.components.StatusPill
import com.eventms.mobile.ui.theme.Green60
import com.eventms.mobile.ui.theme.Slate40
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Attendee
import com.eventms.mobile.domain.Event
import com.eventms.mobile.domain.Money
import com.eventms.mobile.ui.components.EmptyState
import com.eventms.mobile.ui.components.EventCard
import com.eventms.mobile.ui.components.RefreshOnResume
import com.eventms.mobile.ui.components.SectionHeader
import com.eventms.mobile.ui.theme.ScanWarn

// ------------------------------------------------------------- dashboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerDashboardScreen(
    onEventClick: (Long) -> Unit,
    onCreateEvent: () -> Unit,
    onScanTickets: () -> Unit,
    viewModel: OrganizerDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Stats and ticket counts change as people buy; reload on every visit.
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
                    Text(
                        state.organizerName?.let { "Hello, ${it.substringBefore(' ')}" }
                            ?: "Dashboard",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How your events are doing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.pendingScanSyncs > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ScanWarn.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${state.pendingScanSyncs} check-in(s) scanned offline are " +
                                "waiting to sync. Pull to refresh while online.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ScanWarn,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        "Revenue",
                        Money.format(state.stats.totalRevenue),
                        Modifier.weight(1f)
                    )
                    StatTile(
                        "Tickets sold",
                        state.stats.totalTicketsSold.toString(),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Events", state.stats.totalEvents.toString(), Modifier.weight(1f))
                    StatTile(
                        "Published",
                        state.stats.publishedEvents.toString(),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCreateEvent,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New event")
                    }
                    OutlinedButton(
                        onClick = onScanTickets,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Scan")
                    }
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

            if (state.upcoming.isNotEmpty()) {
                item { SectionHeader("Coming up") }
                items(state.upcoming, key = { "up-${it.id}" }) { event ->
                    EventCard(event, { onEventClick(event.id) }, showStatus = true)
                }
            }

            if (state.drafts.isNotEmpty()) {
                item { SectionHeader("Drafts") }
                items(state.drafts, key = { "draft-${it.id}" }) { event ->
                    EventCard(event, { onEventClick(event.id) }, showStatus = true)
                }
            }

            if (state.events.isEmpty() && !state.refreshing) {
                item {
                    EmptyState(
                        title = "No events yet",
                        body = "Create your first event and start selling tickets.",
                        icon = Icons.Outlined.CalendarMonth,
                        actionLabel = "Create an event",
                        onAction = onCreateEvent
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

// ----------------------------------------------------------- events list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerEventsScreen(
    onEventClick: (Long) -> Unit,
    onCreateEvent: () -> Unit,
    onMyTickets: () -> Unit,
    viewModel: OrganizerDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Picks up an event just created or edited without a manual pull.
    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateEvent,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New event") }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text("My events", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(14.dp))
                        // Organizers buy tickets too — to other people's events
                        // and to their own. This is where those live.
                        MyTicketsEntry(onClick = onMyTickets)
                    }
                }

                if (state.events.isEmpty() && !state.refreshing) {
                    item {
                        EmptyState(
                            title = "Nothing here yet",
                            body = "Events you create — drafts included — show up here.",
                            icon = Icons.Outlined.CalendarMonth,
                            actionLabel = "Create an event",
                            onAction = onCreateEvent
                        )
                    }
                }

                items(state.events, key = { it.id }) { event ->
                    EventCard(event, { onEventClick(event.id) }, showStatus = true)
                }

                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }
}

// ----------------------------------------------------------- scan picker

@Composable
fun ScanPickerScreen(
    onPickEvent: (Long) -> Unit,
    viewModel: ScanPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RefreshOnResume { viewModel.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Scan tickets", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick the event you are working the door for.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        if (state.events.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    title = "No published events",
                    body = "Only published events can have people at the door. " +
                        "Publish an event first.",
                    icon = Icons.Outlined.QrCodeScanner
                )
            }
        }

        items(state.events, key = { it.id }) { event ->
            ScanEventRow(event) { onPickEvent(event.id) }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ScanEventRow(event: Event, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${DateTimes.day(event.startDate)} · ${event.location}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${event.ticketsSold} sold",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Icon(
                Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendeesScreen(
    onBack: () -> Unit,
    viewModel: AttendeesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The guest list is what the scanner works from; keep it current.
    RefreshOnResume { viewModel.sync() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.eventTitle.ifBlank { "Guest list" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = viewModel::sync,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Checked in", "${state.checkedIn} / ${state.total}", Modifier.weight(1f))
                        StatTile("Tickets issued", state.ticketsIssued.toString(), Modifier.weight(1f))
                    }
                }

                state.error?.let { msg ->
                    item {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.attendees.isEmpty() && !state.syncing) {
                    item {
                        EmptyState(
                            title = "No attendees yet",
                            body = "When people buy tickets, they appear here.",
                            icon = Icons.Outlined.ConfirmationNumber
                        )
                    }
                }

                items(state.attendees, key = { it.bookingId }) { attendee ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(attendee.email, style = MaterialTheme.typography.titleMedium)
                                StatusPill(
                                    text = if (attendee.isCheckedIn) "Checked in" else attendee.paymentStatus.name,
                                    color = if (attendee.isCheckedIn) Green60 else Slate40
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Ticket: ${attendee.ticketNumber ?: attendee.bookingId}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text("Quantity: ${attendee.quantity}", style = MaterialTheme.typography.bodySmall)
                            if (attendee.isCheckedIn && attendee.checkedInAt != null) {
                                Spacer(Modifier.height(4.dp))
                                Text("Checked in at ${DateTimes.full(attendee.checkedInAt)}", style = MaterialTheme.typography.labelSmall, color = Green60)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/** Doorway from the organizer's Events tab to the tickets they hold themselves. */
@Composable
private fun MyTicketsEntry(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ConfirmationNumber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "My tickets",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Tickets you have bought or are holding",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}