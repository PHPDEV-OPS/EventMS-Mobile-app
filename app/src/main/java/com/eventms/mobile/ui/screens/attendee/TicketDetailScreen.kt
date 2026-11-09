package com.eventms.mobile.ui.screens.attendee

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.core.QrCodes
import com.eventms.mobile.core.rememberCustomTabsLauncher
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Money
import com.eventms.mobile.domain.Ticket
import com.eventms.mobile.ui.components.DetailRow
import com.eventms.mobile.ui.components.LoadingState
import com.eventms.mobile.ui.components.RefreshOnResume
import com.eventms.mobile.ui.components.StatusPill
import com.eventms.mobile.ui.theme.Amber60
import com.eventms.mobile.ui.theme.Green60
import com.eventms.mobile.ui.theme.Slate40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    onBack: () -> Unit,
    viewModel: TicketDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val customTabs = rememberCustomTabsLauncher(prefetchUrl = state.warmUrl)
    val toolbarColor = MaterialTheme.colorScheme.primary
    val onToolbarColor = MaterialTheme.colorScheme.onPrimary

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Opens over the app rather than throwing the customer out to a browser.
    LaunchedEffect(state.paymentUrl) {
        state.paymentUrl?.let { url ->
            customTabs.launch(url, toolbarColor, onToolbarColor)
            viewModel.onPaymentUrlOpened()
        }
    }

    // Coming back from the payment page confirms the ticket automatically.
    RefreshOnResume { viewModel.onReturnedFromPayment() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your ticket") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val ticket = state.ticket

        if (ticket == null) {
            LoadingState(Modifier.padding(padding), "Loading ticket…")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TicketCard(ticket)

            Spacer(Modifier.height(20.dp))

            DetailRow(
                Icons.Outlined.CalendarMonth,
                "When",
                DateTimes.full(ticket.eventStartDate)
            )
            Spacer(Modifier.height(12.dp))
            DetailRow(
                Icons.Outlined.LocationOn,
                "Where",
                ticket.eventLocation ?: "See event page"
            )
            Spacer(Modifier.height(12.dp))
            DetailRow(Icons.Outlined.Person, "Booked by", ticket.email)

            Spacer(Modifier.height(24.dp))

            if (ticket.isPaid) {
                Button(
                    onClick = viewModel::downloadPdf,
                    enabled = !state.downloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.downloading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Download ticket (PDF)")
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "Saved to your Downloads folder. The PDF carries the same QR code, " +
                        "so a printout works at the gate too.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Unpaid: finish the payment before there is anything to download.
                Text("Finish paying", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your seats are still held. Pay " +
                        Money.formatExact(ticket.totalAmount) + " to make this ticket valid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = state.mpesaPhone,
                    onValueChange = viewModel::onMpesaPhoneChange,
                    label = { Text("M-Pesa number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = viewModel::payWithMpesa,
                    enabled = !state.working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.working) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Pay with M-Pesa", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = viewModel::payWithCard,
                    enabled = !state.working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Pay by card instead")
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = viewModel::checkPayment,
                    enabled = !state.working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Check payment")
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "The ticket can be downloaded once the payment clears.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "EVENTMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ticket.eventTitle ?: "Event ticket",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                StatusPill(
                    text = when {
                        ticket.isUsed -> "Used"
                        ticket.isPaid -> "Valid"
                        else -> "Unpaid"
                    },
                    color = when {
                        ticket.isUsed -> Slate40
                        ticket.isPaid -> Green60
                        else -> Amber60
                    }
                )
            }

            Spacer(Modifier.height(22.dp))

            Box(
                modifier = Modifier
                    .size(232.dp)
                    .background(
                        if (ticket.isPaid) Color.White else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (ticket.isPaid) {
                    // The QR the organizer's scanner reads at the gate.
                    val qrBitmap = remember(ticket.qrPayload) {
                        QrCodes.generate(ticket.qrPayload, sizePx = 560)
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Ticket QR code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            "QR unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                    }
                } else {
                    // No QR until it is paid for — it would be refused anyway.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Awaiting payment",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your entry code appears here\nonce the payment clears.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                ticket.ticketNumber ?: ticket.bookingId.take(12).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${ticket.quantity} " +
                    (if (ticket.quantity == 1) "ticket" else "tickets") +
                    " · " + Money.formatExact(ticket.totalAmount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ticket.isUsed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Admitted ${DateTimes.full(ticket.checkedInAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
