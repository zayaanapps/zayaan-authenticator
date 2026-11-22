package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.crypto.QrImageDecoder
import com.example.ui.components.IconTile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onImportQrFromPhoto: () -> Unit,
    onManualEntry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    val scanConsumed = remember { AtomicBoolean(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Authenticator QR") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (hasCameraPermission && cameraRef?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = {
                                torchEnabled = !torchEnabled
                                cameraRef?.cameraControl?.enableTorch(torchEnabled)
                            }
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                                contentDescription = if (torchEnabled) "Turn off flashlight" else "Turn on flashlight"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) {
                    onDispose {
                        analysisExecutor.shutdown()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener(
                            {
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        try {
                                            if (!scanConsumed.get()) {
                                                val decoded = QrImageDecoder.decodeFromImageProxy(imageProxy)
                                                if (!decoded.isNullOrBlank() && scanConsumed.compareAndSet(false, true)) {
                                                    previewView.post {
                                                        onQrCodeScanned(decoded)
                                                    }
                                                }
                                            }
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }

                                    cameraProvider.unbindAll()
                                    cameraRef = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.w("QrScanner", "Camera binding failed: ${e.message}")
                                    errorBanner = "Camera unavailable on this device. You can import a QR image or enter the key manually."
                                }
                            },
                            ContextCompat.getMainExecutor(ctx)
                        )
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Precision Google Authenticator-style viewfinder overlay with glowing corner L-brackets
                val reticleColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = size.minDimension * 0.68f
                    val left = (size.width - boxSize) / 2f
                    val top = (size.height - boxSize) / 2.4f
                    val right = left + boxSize
                    val bottom = top + boxSize
                    val cornerLen = 30.dp.toPx()
                    val bracketStroke = 4.dp.toPx()

                    drawRect(color = Color.Black.copy(alpha = 0.58f))
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(left, top),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                    // Subtle inner frame border
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.25f),
                        topLeft = Offset(left, top),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    // Top-left bracket
                    drawLine(reticleColor, Offset(left, top + cornerLen), Offset(left, top + 6.dp.toPx()), bracketStroke, StrokeCap.Round)
                    drawLine(reticleColor, Offset(left + 6.dp.toPx(), top), Offset(left + cornerLen, top), bracketStroke, StrokeCap.Round)
                    // Top-right bracket
                    drawLine(reticleColor, Offset(right - cornerLen, top), Offset(right - 6.dp.toPx(), top), bracketStroke, StrokeCap.Round)
                    drawLine(reticleColor, Offset(right, top + 6.dp.toPx()), Offset(right, top + cornerLen), bracketStroke, StrokeCap.Round)
                    // Bottom-left bracket
                    drawLine(reticleColor, Offset(left, bottom - cornerLen), Offset(left, bottom - 6.dp.toPx()), bracketStroke, StrokeCap.Round)
                    drawLine(reticleColor, Offset(left + 6.dp.toPx(), bottom), Offset(left + cornerLen, bottom), bracketStroke, StrokeCap.Round)
                    // Bottom-right bracket
                    drawLine(reticleColor, Offset(right - cornerLen, bottom), Offset(right - 6.dp.toPx(), bottom), bracketStroke, StrokeCap.Round)
                    drawLine(reticleColor, Offset(right, bottom - cornerLen), Offset(right, bottom - 6.dp.toPx()), bracketStroke, StrokeCap.Round)
                }

                // Bottom action dock
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    errorBanner?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.74f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Text(
                            text = "Point camera at a 2FA QR code or Google Authenticator export QR",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onImportQrFromPhoto,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scanner_import_image_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Import Image")
                        }
                        FilledTonalButton(
                            onClick = onManualEntry,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scanner_manual_entry_button")
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Enter Key")
                        }
                    }
                }
            } else {
                // Graceful Camera Permission Denied / Rationale State
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        IconTile(
                            icon = Icons.Default.CameraAlt,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 64.dp,
                            iconSize = 32.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Access Needed for QR Scanning",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (permissionRequested) {
                                "Camera permission was denied. You can grant it again, import a QR image using Android Photo Picker, or enter your secret key manually."
                            } else {
                                "Zayaan Authenticator uses the camera strictly on-device to decode 2FA QR codes offline."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("request_camera_permission_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Allow Camera Access")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = onImportQrFromPhoto,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Import Image")
                            }
                            FilledTonalButton(
                                onClick = onManualEntry,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Enter Key")
                            }
                        }
                    }
                }
            }
        }
    }
}
