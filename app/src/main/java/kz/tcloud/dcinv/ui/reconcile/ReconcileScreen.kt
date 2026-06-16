package kz.tcloud.dcinv.ui.reconcile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.domain.QrFormat
import kz.tcloud.dcinv.scanner.QrCodeAnalyzer
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import kz.tcloud.dcinv.util.ScanFeedback
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileScreen(
    onBack: () -> Unit,
    onOpenDevice: (deviceId: Int) -> Unit,
    viewModel: ReconcileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Сверка стойки", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.rackName,
                            style = MaterialTheme.typography.labelMedium,
                            color = DcInvTheme.extra.secondaryText,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clipToBounds()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasPermission) {
                    ReconcileCamera(
                        onCode = { code ->
                            viewModel.onScan(code) { kind ->
                                if (kind == BannerKind.OK) ScanFeedback.success(context)
                                else ScanFeedback.warn(context)
                            }
                        },
                    )
                } else {
                    Text(
                        "Нужен доступ к камере",
                        color = Color.White,
                        modifier = Modifier
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            state.banner?.let { banner ->
                val color = if (banner.kind == BannerKind.OK) DcInvTheme.extra.accent else MaterialTheme.colorScheme.error
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        if (banner.kind == BannerKind.OK) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        banner.text,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (!state.loading) {
                ProgressHeader(confirmed = state.confirmed, total = state.total)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> Checklist(state, onOpenDevice)
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(confirmed: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Подтверждено $confirmed из $total",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else confirmed.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun Checklist(state: ReconcileUiState, onOpenDevice: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.anomalies.isNotEmpty()) {
            item {
                Text(
                    "Расхождения (${state.anomalies.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            items(state.anomalies, key = { it.qrId }) { a ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(a.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            a.qrId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = DcInvTheme.extra.secondaryText,
                        )
                    }
                }
            }
            item {
                Text(
                    "Ожидаемые устройства",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        items(state.expected, key = { it.deviceId }) { item ->
            ExpectedRow(item, onClick = { onOpenDevice(item.deviceId) })
        }
    }
}

@Composable
private fun ExpectedRow(item: ExpectedItem, onClick: () -> Unit) {
    val (icon, tint) = when (item.state) {
        CheckState.CONFIRMED -> Icons.Filled.CheckCircle to DcInvTheme.extra.accent
        CheckState.WRONG_POSITION -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        CheckState.PENDING -> Icons.Filled.RadioButtonUnchecked to DcInvTheme.extra.secondaryText
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = item.position?.let { "U$it" } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = DcInvTheme.extra.secondaryText,
            modifier = Modifier.padding(start = 10.dp).size(width = 34.dp, height = 20.dp),
        )
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.state == CheckState.PENDING) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ReconcileCamera(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // COMPATIBLE (TextureView) composites within Compose bounds and
                // respects overlays; the default SurfaceView punches through and
                // ignores clipping, which made the embedded preview look broken.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        // Continuous: every valid code is forwarded; the ViewModel
                        // dedups so the same label isn't looked up repeatedly.
                        it.setAnalyzer(executor, QrCodeAnalyzer(centerFraction = 0.6f) { raw ->
                            val code = raw.trim()
                            if (QrFormat.isValid(code)) {
                                ContextCompat.getMainExecutor(ctx).execute { onCode(code) }
                            }
                        })
                    }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
