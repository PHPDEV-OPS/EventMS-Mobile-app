package com.eventms.mobile.ui.screens.attendee

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.core.rememberCustomTabsLauncher
import com.eventms.mobile.domain.Money
import com.eventms.mobile.ui.components.RefreshOnResume
import com.eventms.mobile.ui.theme.Green60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onViewTicket: (String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Warms the browser and pre-renders the payment page while the customer is
    // still choosing, so the tab has content the moment it slides up.
    val customTabs = rememberCustomTabsLauncher(prefetchUrl = state.warmUrl)
    val toolbarColor = MaterialTheme.colorScheme.primary
    val onToolbarColor = MaterialTheme.colorScheme.onPrimary

    // Card details are entered on the provider's own page, inside the browser,
    // where this app has no access to them.
    LaunchedEffect(state.paymentUrl) {
        state.paymentUrl?.let { url ->
            customTabs.launch(url, toolbarColor, onToolbarColor)
            viewModel.onPaymentUrlOpened()
        }
    }

    // Closing the tab lands back here; confirm the payment without being asked.
    RefreshOnResume { viewModel.onReturnedFromPayment() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
        ) {
            when (state.stage) {
                CheckoutStage.CONFIRMED ->
                    Confirmation(onViewTicket = { state.bookingId?.let(onViewTicket) })

                CheckoutStage.AWAITING_PAYMENT, CheckoutStage.WORKING ->
                    PaymentStep(
                        state = state,
                        onMethodChange = viewModel::onMethodChange,
                        onPhoneChange = viewModel::onMpesaPhoneChange,
                        onPay = viewModel::pay,
                        onCheck = viewModel::checkPayment,
                        onViewTicket = { state.bookingId?.let(onViewTicket) }
                    )

                else -> CheckoutForm(state, viewModel)
            }
        }
    }
}

@Composable
private fun CheckoutForm(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    Text(
        state.event?.title ?: "Loading…",
        style = MaterialTheme.typography.headlineSmall
    )
    Spacer(Modifier.height(4.dp))
    Text(
        state.event?.location.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(24.dp))

    Text("How many tickets?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { viewModel.onQuantityChange(state.quantity - 1) },
            enabled = state.quantity > 1
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "One fewer")
        }

        Text(
            state.quantity.toString(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        FilledTonalIconButton(
            onClick = { viewModel.onQuantityChange(state.quantity + 1) },
            enabled = state.quantity < state.maxQuantity
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "One more")
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Max ${state.maxQuantity}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        supportingText = { Text("Your ticket is tied to this address") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.phone,
        onValueChange = viewModel::onPhoneChange,
        label = { Text("Phone") },
        supportingText = { Text("Used for the payment and your receipt") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(22.dp))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${state.quantity} × ${Money.format(state.event?.ticketPrice ?: 0.0)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(Money.format(state.total), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(Money.formatExact(state.total), style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    state.error?.let { message ->
        Spacer(Modifier.height(14.dp))
        Banner(message, isError = true)
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = viewModel::submit,
        enabled = state.canSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (state.busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                if (state.isFree) "Reserve my place" else "Continue to payment",
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Your seats are held while you pay.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PaymentStep(
    state: CheckoutUiState,
    onMethodChange: (PaymentMethod) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPay: () -> Unit,
    onCheck: () -> Unit,
    onViewTicket: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text("Your seats are held", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "${state.quantity} ticket(s) for ${state.event?.title.orEmpty()} — " +
                Money.formatExact(state.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(22.dp))

        Text("How would you like to pay?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MethodCard(
                title = "M-Pesa",
                subtitle = "PIN prompt on your phone",
                icon = Icons.Outlined.PhoneAndroid,
                selected = state.method == PaymentMethod.MPESA,
                onClick = { onMethodChange(PaymentMethod.MPESA) },
                modifier = Modifier.weight(1f)
            )
            MethodCard(
                title = "Card",
                subtitle = "Pay in your browser",
                icon = Icons.Outlined.CreditCard,
                selected = state.method == PaymentMethod.CARD,
                onClick = { onMethodChange(PaymentMethod.CARD) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (state.method == PaymentMethod.MPESA) {
            OutlinedTextField(
                value = state.mpesaPhone,
                onValueChange = onPhoneChange,
                label = { Text("M-Pesa number") },
                supportingText = { Text("The prompt goes to this number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                "You'll be taken to a secure page to enter your card details, " +
                    "then come back here and tap Check payment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.notice?.let {
            Spacer(Modifier.height(14.dp))
            Banner(it, isError = false)
        }
        state.error?.let {
            Spacer(Modifier.height(14.dp))
            Banner(it, isError = true)
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onPay,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    if (state.method == PaymentMethod.MPESA) {
                        "Send M-Pesa request"
                    } else {
                        "Pay ${Money.format(state.total)}"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCheck,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Check payment")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onViewTicket,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("View booking")
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "You can leave and finish this later from My tickets.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Confirmation(onViewTicket: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = Green60,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("You're going!", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "Payment confirmed. Your ticket QR is ready to be scanned at the gate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = onViewTicket,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("View my ticket", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Banner(message: String, isError: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(14.dp)
        )
    }
}

/** One of the two payment options, shown as a selectable card. */
@Composable
private fun MethodCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}