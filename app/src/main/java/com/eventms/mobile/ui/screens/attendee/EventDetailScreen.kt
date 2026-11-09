package com.eventms.mobile.ui.screens.attendee

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Event
import com.eventms.mobile.domain.Money
import com.eventms.mobile.ui.components.DetailRow
import com.eventms.mobile.ui.components.ErrorState
import com.eventms.mobile.ui.components.LoadingState
import com.eventms.mobile.ui.components.SectionHeader
import com.eventms.mobile.ui.components.StatusPill
import com.eventms.mobile.ui.components.RefreshOnResume
import com.eventms.mobile.ui.theme.Red500
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onBookTickets: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Ticket counts move while someone is looking at the page.
    RefreshOnResume { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.event?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            state.event?.let { event ->
                BookingBar(event = event, onBookTickets = { onBookTickets(event.id) })
            }
        }
    ) { padding ->
        when {
            state.loading && state.event == null ->
                LoadingState(Modifier.padding(padding), "Loading event…")

            state.event == null ->
                ErrorState(
                    state.error ?: "This event could not be loaded",
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding)
                )

            else -> {
                val event = state.event!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    item { Hero(event) }

                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                            Text(event.title, style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(14.dp))

                            DetailRow(
                                Icons.Outlined.CalendarMonth,
                                "Date",
                                DateTimes.day(event.startDate)
                            )
                            Spacer(Modifier.height(12.dp))
                            DetailRow(
                                Icons.Outlined.Schedule,
                                "Doors",
                                "${DateTimes.time(event.startDate)} — ${DateTimes.time(event.endDate)}"
                            )
                            Spacer(Modifier.height(12.dp))
                            DetailRow(Icons.Outlined.LocationOn, "Venue", event.location)
                            Spacer(Modifier.height(12.dp))
                            DetailRow(
                                Icons.Outlined.ConfirmationNumber,
                                "Availability",
                                if (event.isSoldOut) {
                                    "Sold out"
                                } else {
                                    "${event.availableTickets} of ${event.totalTickets} left"
                                }
                            )
                        }
                    }

                    if (!event.description.isNullOrBlank()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                Text("About", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    event.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(22.dp))
                            }
                        }
                    }

                    item {
                        VenueMapSection(
                            event = event,
                            venue = state.venue,
                            lookupFailed = state.venueLookupFailed,
                            onDirections = {
                                val query = state.venue?.let { "${it.latitude},${it.longitude}" }
                                    ?: Uri.encode(event.location)
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                )
                                context.startActivity(intent)
                            }
                        )
                    }

                    if (state.pastEvents.isNotEmpty()) {
                        item {
                            Column {
                                SectionHeader(
                                    "More from this organizer",
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Other events they have run.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(Modifier.height(12.dp))

                                LazyRow(
                                    contentPadding = androidx.compose.foundation.layout
                                        .PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.pastEvents, key = { it.id }) { past ->
                                        OrganizerHistoryCard(past) { onEventClick(past.id) }
                                    }
                                }
                                Spacer(Modifier.height(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(event: Event) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
    ) {
        if (event.coverImageUrl.isBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else {
            AsyncImage(
                model = event.coverImageUrl,
                contentDescription = event.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            event.category?.takeIf { it.isNotBlank() }?.let {
                StatusPill(it, Color.White)
            }
            if (event.isPast) StatusPill("Ended", Red500)
        }
    }
}

/**
 * The venue on Google Maps. Uses the organizer's pin when they set one and falls
 * back to a geocode of the venue text, which is what most events have.
 */
@Composable
private fun VenueMapSection(
    event: Event,
    venue: VenueLocation?,
    lookupFailed: Boolean,
    onDirections: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Getting there", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            event.location,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Card(
            onClick = onDirections,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            when {
                venue != null -> Box(Modifier.fillMaxSize()) {
                    val position = LatLng(venue.latitude, venue.longitude)
                    val cameraPositionState = rememberCameraPositionState {
                        this.position = CameraPosition.fromLatLngZoom(position, 15f)
                    }

                    // Lite mode: a static image of the map rather than the full
                    // interactive engine. It is the right tool for a preview —
                    // far lighter, and it cannot swallow the page's scroll the
                    // way a live map nested in a scrolling list does.
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            mapToolbarEnabled = false
                        ),
                        onMapClick = { onDirections() }
                    ) {
                        Marker(
                            state = MarkerState(position = position),
                            title = event.title,
                            snippet = event.location
                        )
                    }

                    // Says what tapping does, without covering the pin.
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Directions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Tap for directions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                lookupFailed -> MapPlaceholder(
                    "The venue could not be placed on the map. " +
                        "Directions will search for the address instead."
                )

                else -> MapPlaceholder("Locating the venue...")
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onDirections,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Directions, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in Google Maps")
        }

        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun MapPlaceholder(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun OrganizerHistoryCard(event: Event, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(210.dp)
    ) {
        Column {
            if (event.coverImageUrl.isNotBlank()) {
                AsyncImage(
                    model = event.coverImageUrl,
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Column(Modifier.padding(12.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    DateTimes.short(event.startDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BookingBar(event: Event, onBookTickets: () -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Per ticket",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    Money.format(event.ticketPrice),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Button(
                onClick = onBookTickets,
                enabled = event.isOnSale,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Text(
                    when {
                        event.isPast -> "Event ended"
                        event.isSoldOut -> "Sold out"
                        !event.isOnSale -> "Unavailable"
                        else -> "Get tickets"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
