package com.eventms.mobile.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Event
import com.eventms.mobile.domain.EventStatus
import com.eventms.mobile.domain.Money
import com.eventms.mobile.ui.theme.Amber60
import com.eventms.mobile.ui.theme.Green60
import com.eventms.mobile.ui.theme.Red500
import com.eventms.mobile.ui.theme.Slate40

@Composable
fun EventCard(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showStatus: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                EventImage(event)

                // Scrim so the overlaid chips stay readable on bright photos.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.45f)
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showStatus) {
                        StatusPill(event.status.name, event.status.tint())
                    }
                    event.category?.takeIf { it.isNotBlank() }?.let {
                        StatusPill(it, Color.White)
                    }
                }

                Text(
                    text = if (event.isFree) "Free" else Money.format(event.ticketPrice),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                )
            }

            Column(Modifier.padding(16.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                MetaLine(
                    Icons.Outlined.CalendarMonth,
                    "${DateTimes.day(event.startDate)} · ${DateTimes.time(event.startDate)}"
                )
                Spacer(Modifier.height(5.dp))
                MetaLine(Icons.Outlined.LocationOn, event.location)

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = availabilityTint(event)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        availabilityLabel(event),
                        style = MaterialTheme.typography.labelLarge,
                        color = availabilityTint(event)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        DateTimes.relative(event.startDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EventImage(event: Event) {
    // coverImageUrl falls back to a stock still, so this is only reached if
    // even that is somehow blank.
    if (event.coverImageUrl.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(40.dp)
            )
        }
    } else {
        AsyncImage(
            model = event.coverImageUrl,
            contentDescription = event.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun availabilityLabel(event: Event): String = when {
    event.isPast -> "Event has ended"
    event.isSoldOut -> "Sold out"
    event.availableTickets <= 10 -> "Only ${event.availableTickets} left"
    else -> "${event.availableTickets} tickets left"
}

@Composable
private fun availabilityTint(event: Event): Color = when {
    event.isPast -> Slate40
    event.isSoldOut -> Red500
    event.availableTickets <= 10 -> Amber60
    else -> Green60
}

fun EventStatus.tint(): Color = when (this) {
    EventStatus.PUBLISHED -> Green60
    EventStatus.DRAFT -> Amber60
    EventStatus.CANCELLED -> Red500
    EventStatus.COMPLETED -> Slate40
}
