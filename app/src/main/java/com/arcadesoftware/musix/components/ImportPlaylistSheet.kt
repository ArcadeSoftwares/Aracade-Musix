package com.arcadesoftware.musix.components

import android.Manifest
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.arcadesoftware.musix.db.AppDatabase
import com.arcadesoftware.musix.db.entities.PlayHistoryEntity
import com.arcadesoftware.musix.db.entities.PlaylistEntity
import com.arcadesoftware.musix.db.entities.PlaylistSongEntity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

// ─────────────────────────────────────────────────────────────────────────────
// Import mode enum
// ─────────────────────────────────────────────────────────────────────────────
enum class ImportMode { MENU, CREATE, SCAN_QR, SPOTIFY }

// ─────────────────────────────────────────────────────────────────────────────
// Main bottom sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistSheet(
    onDismiss: () -> Unit,
    onPlaylistCreated: (String) -> Unit,
    onQrImported: (name: String, songs: List<PlayHistoryEntity>) -> Unit,
    onSpotifyImport: (playlistId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(ImportMode.MENU) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp).height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
            )
        }
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            },
            label = "import_mode"
        ) { currentMode ->
            when (currentMode) {
                ImportMode.MENU -> ImportMenuPane(
                    onCreateNew = { mode = ImportMode.CREATE },
                    onScanQr = { mode = ImportMode.SCAN_QR },
                    onSpotify = { mode = ImportMode.SPOTIFY }
                )
                ImportMode.CREATE -> CreatePlaylistPane(
                    onBack = { mode = ImportMode.MENU },
                    onDismiss = onDismiss,
                    onCreated = onPlaylistCreated
                )
                ImportMode.SCAN_QR -> ScanQrPane(
                    onBack = { mode = ImportMode.MENU },
                    onImported = { name, songs ->
                        onQrImported(name, songs)
                        onDismiss()
                    }
                )
                ImportMode.SPOTIFY -> SpotifyImportPane(
                    onBack = { mode = ImportMode.MENU },
                    onImport = { id ->
                        onSpotifyImport(id)
                        onDismiss()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Menu pane — 3 options
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ImportMenuPane(
    onCreateNew: () -> Unit,
    onScanQr: () -> Unit,
    onSpotify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add Playlist",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Choose how you'd like to add a playlist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        ImportOptionCard(
            icon = Icons.Rounded.PlaylistAdd,
            title = "Create New Playlist",
            subtitle = "Start fresh with a custom name",
            gradient = Brush.linearGradient(listOf(Color(0xFFFA243C), Color(0xFFFF6B6B))),
            onClick = onCreateNew
        )
        Spacer(Modifier.height(12.dp))
        ImportOptionCard(
            icon = Icons.Rounded.QrCodeScanner,
            title = "Scan Musix QR",
            subtitle = "Import a playlist shared via QR code",
            gradient = Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF9C8CFF))),
            onClick = onScanQr
        )
        Spacer(Modifier.height(12.dp))
        ImportOptionCard(
            icon = Icons.Rounded.Link,
            title = "Import from Spotify",
            subtitle = "Paste a Spotify playlist URL",
            gradient = Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF1ED760))),
            onClick = onSpotify
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.12f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Create playlist pane
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CreatePlaylistPane(
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PaneHeader(title = "New Playlist", onBack = onBack)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Playlist name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.4f)
            )
        )
        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Cancel") }

            Button(
                onClick = { onCreated(name.trim()); onDismiss() },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA243C))
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR scan pane — live camera with ML Kit
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ScanQrPane(
    onBack: () -> Unit,
    onImported: (String, List<PlayHistoryEntity>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<Triple<String, List<PlayHistoryEntity>, Boolean>?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PaneHeader(title = "Scan Musix QR", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        if (!hasCameraPermission) {
            // No permission state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(48.dp))
                    Text("Camera permission required", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else if (scanResult != null) {
            // Show scanned result
            val (plName, songs, expired) = scanResult!!
            ScanResultCard(
                playlistName = plName,
                songCount = songs.size,
                isExpired = expired,
                onImport = { if (!expired) onImported(plName, songs) },
                onRescan = { scanResult = null; isScanning = true; scanError = null }
            )
        } else {
            // ── Beautiful Camera Viewfinder ─────────────────────────────────────────
            val laserAnim = rememberInfiniteTransition(label = "laser")
            val laserY by laserAnim.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laser_y"
            )
            val rotation by laserAnim.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
                label = "border_rotation"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .rotatingGlowBorder(rotation = rotation, strokeWidth = 3.dp, cornerRadius = 28.dp)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(25.dp))
            ) {
                // Live camera preview
                val executor = remember { Executors.newSingleThreadExecutor() }
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        if (isScanning) {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build()
                                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                val scanner = BarcodeScanning.getClient()
                                val analysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                analysis.setAnalyzer(executor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage, imageProxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT }
                                                    ?.rawValue?.let { raw ->
                                                        coroutineScope.launch {
                                                            val decoded = PlaylistQRCoder.decodePlaylist(raw)
                                                            if (decoded != null) {
                                                                isScanning = false
                                                                scanResult = decoded
                                                            } else {
                                                                scanError = "Not a Musix QR code"
                                                            }
                                                        }
                                                    }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else imageProxy.close()
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview, analysis
                                    )
                                } catch (e: Exception) {
                                    scanError = "Camera error: ${e.localizedMessage}"
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    }
                )

                // ── Gradient overlay: top + bottom vignette ─────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.35f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.35f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                // ── Animated laser line inside the scan zone ─────────────────
                Box(modifier = Modifier.fillMaxSize()) {
                    val laserColor1 = Color(0xFFFA243C)
                    val laserColor2 = Color(0xFF6C63FF)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(2.dp)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                val zoneHeight = 320.dp.toPx() - 56.dp.toPx() * 2
                                translationY = (laserY - 0.5f) * zoneHeight
                            }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        laserColor2.copy(alpha = 0.9f),
                                        laserColor1.copy(alpha = 0.9f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // ── Top label inside camera ─────────────────────────────────────
                Text(
                    text = "🎵  Musix QR Scanner",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            if (scanError != null) {
                Text(scanError!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Point your camera at a Musix QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}


@Composable
private fun ScanResultCard(
    playlistName: String,
    songCount: Int,
    isExpired: Boolean,
    onImport: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (isExpired) MaterialTheme.colorScheme.error.copy(0.4f) else Color(0x336C63FF), RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (isExpired) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isExpired) MaterialTheme.colorScheme.error else Color(0xFF6C63FF),
            modifier = Modifier.size(40.dp)
        )
        Text(
            if (isExpired) "QR Code Expired" else "Playlist Found!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            "\"$playlistName\"  •  $songCount songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (isExpired) {
            Text(
                "This QR code has expired (60s limit). Ask the sender to generate a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onRescan, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("Scan Again", color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRescan, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Scan Again")
                }
                Button(
                    onClick = onImport,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                ) { Text("Import", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spotify import pane
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SpotifyImportPane(
    onBack: () -> Unit,
    onImport: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    val spotifyIdRegex = Regex("(?:open\\.spotify\\.com/playlist/|spotify:playlist:)([A-Za-z0-9]+)")

    val playlistId = remember(url) { spotifyIdRegex.find(url)?.groupValues?.getOrNull(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PaneHeader(title = "Import from Spotify", onBack = onBack)
        Spacer(Modifier.height(24.dp))

        // Spotify branding header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1DB954).copy(0.15f), Color(0xFF1DB954).copy(0.05f))))
                .border(1.dp, Color(0x261DB954), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1DB954)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column {
                Text("Spotify Playlist", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Paste the playlist URL or share link", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Spotify playlist URL") },
            placeholder = { Text("https://open.spotify.com/playlist/...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { url = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            supportingText = {
                if (url.isNotBlank() && playlistId == null) {
                    Text("⚠ Doesn't look like a valid Spotify playlist URL", color = MaterialTheme.colorScheme.error)
                } else if (playlistId != null) {
                    Text("✓ Playlist ID: $playlistId", color = Color(0xFF1DB954))
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1DB954),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.4f)
            )
        )

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Cancel") }

            Button(
                onClick = { playlistId?.let { onImport(it) } },
                enabled = playlistId != null,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
            ) { Text("Import", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared header with back button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PaneHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
