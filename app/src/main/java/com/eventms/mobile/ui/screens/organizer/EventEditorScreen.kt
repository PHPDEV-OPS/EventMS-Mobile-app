package com.eventms.mobile.ui.screens.organizer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import com.eventms.mobile.data.repository.PlaceSuggestion
import com.eventms.mobile.ui.theme.Green60
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Card
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.eventms.mobile.domain.EventCategories
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.EventStatus
import com.eventms.mobile.ui.components.LoadingState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    onViewAttendees: (Long) -> Unit,
    onScanTickets: (Long) -> Unit,
    eventId: Long,
    viewModel: EventEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedEventId) { state.savedEventId?.let(onSaved) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New event" else "Edit event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete event",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding), "Loading event…")
            return@Scaffold
        }

        val form = state.form

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
        ) {
            if (!state.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onViewAttendees(eventId) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guest list")
                    }
                    OutlinedButton(
                        onClick = { onScanTickets(eventId) },
                        modifier = Modifier.weight(1f)
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
                Spacer(Modifier.height(20.dp))
            }

            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                label = { Text("Event title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = form.description,
                onValueChange = { value -> viewModel.update { it.copy(description = value) } },
                label = { Text("Description") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            VenueAutocompleteField(
                query = form.location,
                suggestions = state.venueSuggestions,
                searching = state.searchingVenues,
                confirmed = state.venueConfirmed || form.latitude != null,
                onQueryChange = viewModel::onVenueQueryChange,
                onSelect = viewModel::onVenueSelected
            )

            Spacer(Modifier.height(20.dp))

            DateTimeField(
                label = "Starts",
                value = form.startDate,
                onChange = { value -> viewModel.update { it.copy(startDate = value) } }
            )

            Spacer(Modifier.height(12.dp))

            DateTimeField(
                label = "Ends",
                value = form.endDate,
                onChange = { value -> viewModel.update { it.copy(endDate = value) } }
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.ticketPrice,
                    onValueChange = { value -> viewModel.update { it.copy(ticketPrice = value) } },
                    label = { Text("Price (KSh)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.totalTickets,
                    onValueChange = { value -> viewModel.update { it.copy(totalTickets = value) } },
                    label = { Text("Capacity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            CategoryPicker(
                selected = form.category,
                onSelect = { value -> viewModel.update { it.copy(category = value) } }
            )

            Spacer(Modifier.height(20.dp))

            CoverImagePicker(
                imageUrl = form.imageUrl,
                category = form.category,
                uploading = state.uploadingImage,
                onPick = viewModel::onCoverImagePicked,
                onClear = viewModel::onCoverImageCleared
            )

            Spacer(Modifier.height(20.dp))

            Text("Visibility", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    EventStatus.DRAFT,
                    EventStatus.PUBLISHED,
                    EventStatus.CANCELLED
                ).forEach { status ->
                    FilterChip(
                        selected = form.status == status,
                        onClick = { viewModel.update { it.copy(status = status) } },
                        label = { Text(status.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            Text(
                "Only published events are visible to attendees and can sell tickets.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            state.error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (state.isNew) "Create event" else "Save changes",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this event?") },
            text = {
                Text(
                    "This removes the event for everyone. Bookings already made will " +
                        "no longer be linked to it."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun Spacer(modifier: Modifier) = androidx.compose.foundation.layout.Spacer(modifier)

/** Date and time chosen in two steps, matching the platform pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    value: LocalDateTime?,
    onChange: (LocalDateTime) -> Unit
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDateTime?>(null) }

    OutlinedTextField(
        value = value?.let { DateTimes.full(it) } ?: "Not set",
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = { showDate = true }) { Text("Pick date") }
        TextButton(
            onClick = { showTime = true },
            enabled = value != null
        ) { Text("Pick time") }
    }

    if (showDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value
                ?.toInstant(ZoneOffset.UTC)
                ?.toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            val base = value ?: LocalDateTime.now().withHour(18).withMinute(0)
                            val combined = picked.atTime(base.hour, base.minute)
                            pendingDate = combined
                            onChange(combined)
                        }
                        showDate = false
                        // Straight into the time picker; a date alone is rarely enough.
                        showTime = true
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        val base = pendingDate ?: value ?: LocalDateTime.now()
        val timePickerState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Pick a time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(
                            base.withHour(timePickerState.hour)
                                .withMinute(timePickerState.minute)
                        )
                        showTime = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Cancel") }
            }
        )
    }
}

/** The fixed category list, so filters stay meaningful across organizers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text("Category", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EventCategories.ALL.forEach { category ->
                FilterChip(
                    selected = selected.equals(category, ignoreCase = true),
                    onClick = { onSelect(category) },
                    label = { Text(category) }
                )
            }
        }
    }
}

/**
 * Cover image, chosen from the device.
 *
 * Uses the photo picker, which needs no storage permission at all — the system
 * hands back a single image the person deliberately chose. Leaving it empty is
 * fine: a stock image for the category is shown instead of a blank card.
 */
@Composable
private fun CoverImagePicker(
    imageUrl: String,
    category: String,
    uploading: Boolean,
    onPick: (Uri) -> Unit,
    onClear: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPick) }

    val launch = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Column {
        Text("Cover image", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Card(
            onClick = { if (!uploading) launch() },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = imageUrl.ifBlank { EventCategories.defaultCoverFor(category) },
                    contentDescription = "Event cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (uploading) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    }
                } else if (imageUrl.isBlank()) {
                    // Make it obvious this is a stand-in, not their photo.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap to choose a photo",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                "Optional - a $category image is used otherwise",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        if (imageUrl.isNotBlank() && !uploading) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = launch) { Text("Change photo") }
                TextButton(onClick = onClear) { Text("Remove") }
            }
        }
    }
}

/**
 * Venue field backed by Google Places.
 *
 * Free-typed venues are the reason event pages end up pointing at the wrong
 * side of town: a geocoder will happily turn a typo into coordinates somewhere.
 * Picking from real places means the venue provably exists, and we get Google's
 * own coordinates for the map instead of a guess.
 *
 * Typing is never blocked — if suggestions fail to load, whatever was typed is
 * still saved and the map falls back to geocoding it.
 */
@Composable
private fun VenueAutocompleteField(
    query: String,
    suggestions: List<PlaceSuggestion>,
    searching: Boolean,
    confirmed: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (PlaceSuggestion) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Venue") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.LocationOn, contentDescription = null)
            },
            trailingIcon = {
                when {
                    searching -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    confirmed -> Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Venue confirmed on Google Maps",
                        tint = Green60
                    )
                }
            },
            supportingText = {
                Text(
                    if (confirmed) {
                        "Pinned on Google Maps - attendees will see the exact spot."
                    } else {
                        "Start typing and pick your venue from the list."
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    suggestions.forEachIndexed { index, suggestion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(suggestion) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    suggestion.primaryText,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (suggestion.secondaryText.isNotBlank()) {
                                    Text(
                                        suggestion.secondaryText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (index != suggestions.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 44.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}