package kz.tcloud.dcinv.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kz.tcloud.dcinv.domain.QrFormat
import kz.tcloud.dcinv.scanner.QrCodeAnalyzer
import java.util.concurrent.Executors

private val FRAME_SIZE = 250.dp
private val GLASS = Color(0x66000000) // black/40

@Composable
fun ScannerScreen(
    onQrScanned: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var manualEntryOpen by remember { mutableStateOf(false) }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(
                    onQrScanned = onQrScanned,
                    onMessage = { message = it },
                    onCameraReady = { camera = it },
                )
                ScanOverlay()
            } else {
                PermissionRationale(
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onManualEntry = { manualEntryOpen = true },
                )
            }

            // Top controls: back (glass) + center pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, desc = "Назад", onClick = onBack)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(50), color = GLASS) {
                        Text(
                            text = "Scan Asset QR",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // Spacer to balance the back button width.
                Box(modifier = Modifier.size(44.dp))
            }

            // Bottom controls: round flashlight + wide manual entry.
            if (hasPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (torchOn) MaterialTheme.colorScheme.primary else GLASS,
                        modifier = Modifier.size(56.dp),
                    ) {
                        IconButton(
                            enabled = hasFlash,
                            onClick = {
                                torchOn = !torchOn
                                camera?.cameraControl?.enableTorch(torchOn)
                            },
                        ) {
                            Icon(
                                imageVector = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                contentDescription = "Фонарик",
                                tint = Color.White,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GLASS,
                        modifier = Modifier.weight(1f),
                    ) {
                        TextButton(
                            onClick = { manualEntryOpen = true },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Filled.Keyboard, contentDescription = null, tint = Color.White)
                            Text(
                                "Manual Entry",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (manualEntryOpen) {
        ManualEntryDialog(
            onDismiss = { manualEntryOpen = false },
            onSubmit = { code ->
                manualEntryOpen = false
                onQrScanned(code)
            },
        )
    }
}

/** Dark scrim with a transparent rounded cutout, corner brackets + laser line. */
@Composable
private fun ScanOverlay() {
    val transition = rememberInfiniteTransition(label = "laser")
    val laser by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "laserY",
    )
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frame = FRAME_SIZE.toPx()
        val left = (size.width - frame) / 2f
        val top = (size.height - frame) / 2f
        val radius = 16.dp.toPx()

        // Scrim everywhere except the rounded center square (even-odd cutout).
        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(
                RoundRect(
                    Rect(left, top, left + frame, top + frame),
                    CornerRadius(radius, radius),
                ),
            )
            fillType = PathFillType.EvenOdd
        }
        drawPath(path, Color.Black.copy(alpha = 0.7f))

        // Corner brackets (white).
        val len = 28.dp.toPx()
        val stroke = 4.dp.toPx()
        val white = Color.White
        // top-left
        drawLine(white, Offset(left, top + radius), Offset(left, top + len), stroke)
        drawLine(white, Offset(left + radius, top), Offset(left + len, top), stroke)
        // top-right
        drawLine(white, Offset(left + frame, top + radius), Offset(left + frame, top + len), stroke)
        drawLine(white, Offset(left + frame - radius, top), Offset(left + frame - len, top), stroke)
        // bottom-left
        drawLine(white, Offset(left, top + frame - radius), Offset(left, top + frame - len), stroke)
        drawLine(white, Offset(left + radius, top + frame), Offset(left + len, top + frame), stroke)
        // bottom-right
        drawLine(white, Offset(left + frame, top + frame - radius), Offset(left + frame, top + frame - len), stroke)
        drawLine(white, Offset(left + frame - radius, top + frame), Offset(left + frame - len, top + frame), stroke)

        // Laser line sweeping inside the frame.
        val y = top + (frame * laser).coerceIn(0f, frame)
        drawLine(primary, Offset(left + 8f, y), Offset(left + frame - 8f, y), 2.dp.toPx())
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Surface(shape = CircleShape, color = GLASS, modifier = Modifier.size(44.dp)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = desc, tint = Color.White)
        }
    }
}

@Composable
private fun PermissionRationale(onGrant: () -> Unit, onManualEntry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Для сканирования нужен доступ к камере",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Button(onClick = onGrant) { Text("Разрешить") }
        TextButton(onClick = onManualEntry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Ввести ID вручную", color = Color.White)
        }
    }
}

@Composable
private fun ManualEntryDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf(QrFormat.PREFIX) }
    val normalized = text.trim().uppercase()
    val valid = QrFormat.isValid(normalized)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ввести ID QR") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("DCQR-XXXXXXXX") },
                    isError = text.isNotBlank() && text != QrFormat.PREFIX && !valid,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Формат: DCQR- и 8 символов (без I, O, 0, 1)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(normalized) }, enabled = valid) { Text("Открыть") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CameraPreview(
    onQrScanned: (String) -> Unit,
    onMessage: (String) -> Unit,
    onCameraReady: (Camera) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // Deterministic crop so the analyzer's center-fraction matches the
                // visible reticle the same way on every device.
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            // Accept only codes near the center, matching the 250.dp
                            // on-screen reticle. PreviewView FILL_CENTER crops the
                            // sides, so the frame ≈ 0.4 of the image width, not 0.7.
                            QrCodeAnalyzer(centerFraction = 0.4f) { raw ->
                                if (handled.value) return@QrCodeAnalyzer
                                val code = raw.trim()
                                val main = ContextCompat.getMainExecutor(ctx)
                                if (QrFormat.isValid(code)) {
                                    handled.value = true
                                    main.execute { onQrScanned(code) }
                                } else {
                                    main.execute { onMessage("Этот QR не относится к системе") }
                                }
                            },
                        )
                    }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    ).also(onCameraReady)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
