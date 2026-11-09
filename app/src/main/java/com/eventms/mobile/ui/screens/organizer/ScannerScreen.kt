package com.eventms.mobile.ui.screens.organizer

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventms.mobile.domain.ScanOutcome
import com.eventms.mobile.domain.ScanResult
import com.eventms.mobile.ui.theme.ScanFail
import com.eventms.mobile.ui.theme.ScanPass
import com.eventms.mobile.ui.theme.ScanWarn
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scan tickets", style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.eventTitle.ifBlank { "Loading event…" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleTorch) {
                        Icon(
                            if (state.torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                            contentDescription = "Toggle torch"
                        )
                    }
                    if (state.pendingSyncs > 0) {
                        IconButton(onClick = viewModel::syncNow) {
                            Icon(Icons.Outlined.CloudSync, contentDescription = "Sync check-ins")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScanCounters(state)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .background(Color.Black, RoundedCornerShape(20.dp))
            ) {
                if (cameraPermission.status.isGranted) {
                    CameraPreview(
                        torchOn = state.torchOn,
                        onBarcode = viewModel::onBarcodeDetected
                    )
                    ScanReticle()
                } else {
                    PermissionPrompt { cameraPermission.launchPermissionRequest() }
                }

                state.lastOutcome?.let { outcome ->
                    ScanVerdict(
                        outcome = outcome,
                        onDismiss = viewModel::dismissOutcome,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            ManualEntry(
                value = state.manualEntry,
                onValueChange = viewModel::onManualEntryChange,
                onSubmit = viewModel::submitManualEntry
            )
        }
    }
}

@Composable
private fun ScanCounters(state: ScannerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Counter("Admitted", state.checkedIn.toString(), Modifier.weight(1f))
        Counter("On the list", state.total.toString(), Modifier.weight(1f))
        if (state.pendingSyncs > 0) {
            Counter("To sync", state.pendingSyncs.toString(), Modifier.weight(1f), ScanWarn)
        }
    }
}

@Composable
private fun Counter(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 12.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * CameraX preview feeding frames to ML Kit.
 *
 * The analyser runs on its own single-thread executor and uses
 * KEEP_ONLY_LATEST so a slow verification round-trip cannot back up the queue.
 */
@Composable
private fun CameraPreview(torchOn: Boolean, onBarcode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    DisposableEffect(torchOn, camera) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(torchOn)
        onDispose { }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                        ?.rawValue
                                        ?.let(onBarcode)
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }

                runCatching {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
private fun ScanReticle() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
        )
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Camera access needed",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The scanner reads the QR code on each attendee's ticket.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

/** The verdict panel. Colour does the talking — this gets read at a glance. */
@Composable
private fun ScanVerdict(
    outcome: ScanOutcome,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = when (outcome.result) {
        ScanResult.VALID -> ScanPass
        ScanResult.ALREADY_CHECKED_IN -> ScanWarn
        else -> ScanFail
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        shape = RoundedCornerShape(18.dp),
        color = tint
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                when (outcome.result) {
                    ScanResult.VALID -> "ADMIT"
                    ScanResult.ALREADY_CHECKED_IN -> "ALREADY IN"
                    ScanResult.NOT_PAID -> "NOT PAID"
                    ScanResult.CANCELLED -> "CANCELLED"
                    ScanResult.WRONG_EVENT -> "WRONG EVENT"
                    ScanResult.NOT_FOUND -> "NOT VALID"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                outcome.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )

            outcome.attendeeEmail?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            outcome.ticketNumber?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            if (outcome.pendingSync) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recorded offline — will sync when the connection returns.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan next")
            }
        }
    }
}

@Composable
private fun ManualEntry(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Or type a ticket number") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onSubmit,
            enabled = value.isNotBlank(),
            modifier = Modifier.height(56.dp)
        ) {
            Text("Check")
        }
    }
}
