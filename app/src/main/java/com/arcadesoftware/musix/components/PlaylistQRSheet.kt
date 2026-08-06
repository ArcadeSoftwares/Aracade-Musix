package com.arcadesoftware.musix.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import com.arcadesoftware.musix.db.entities.PlayHistoryEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// ── HOW THIS WORKS (V4 QR) ───────────────────────────────────────────────────
//
// To support sharing playlists with 1000+ songs without making the QR code
// an unscannable dense square, we upload the payload to a public Firestore
// collection (`shared_playlists`) and only encode the Document ID into the QR.
// The QR payload is always ~27 chars ("musix4:<20-char-doc-id>").
// ─────────────────────────────────────────────────────────────────────────────

private const val QR_SCHEME_V4 = "musix4:"
private const val QR_SCHEME_V1 = "musix1:"
private const val KEY = "MusixAppShareKey1234567890123456"

object PlaylistQRCoder {
    private var cachedHash: Int = 0
    private var cachedQrData: String? = null
    private var cachedDocId: String? = null

    class RateLimitException : Exception("RATE_LIMIT_EXCEEDED")

    /** Uploads playlist to Firestore and returns the Document ID. Falls back to compressed payload if offline. */
    suspend fun encodePlaylist(context: android.content.Context, name: String, songs: List<PlayHistoryEntity>): String {
        val prefs = context.getSharedPreferences("qr_rate_limit", android.content.Context.MODE_PRIVATE)
        val blockedUntil = prefs.getLong("blocked_until", 0L)
        if (System.currentTimeMillis() < blockedUntil) {
            throw RateLimitException()
        }

        val timestampsStr = prefs.getString("timestamps", "") ?: ""
        val generationTimestamps = timestampsStr.split(",")
            .mapNotNull { it.toLongOrNull() }
            .toMutableList()

        val oneMinAgo = System.currentTimeMillis() - 60_000
        generationTimestamps.removeAll { it < oneMinAgo }
        
        if (generationTimestamps.size >= 5) {
            prefs.edit().putLong("blocked_until", System.currentTimeMillis() + 10 * 60 * 1000).apply()
            throw RateLimitException()
        }
        
        generationTimestamps.add(System.currentTimeMillis())
        prefs.edit().putString("timestamps", generationTimestamps.joinToString(",")).apply()

        val currentHash = name.hashCode() * 31 + songs.hashCode()
        if (currentHash == cachedHash && cachedQrData != null) {
            if (cachedDocId != null) {
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.arcadesoftware.musix.workers.DeleteSharedPlaylistWorker>()
                    .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES)
                    .setInputData(androidx.work.Data.Builder().putString("docId", cachedDocId).build())
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()
                
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "delete_qr_$cachedDocId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            return cachedQrData!!
        }

        try {
            val arr = songs.map { s ->
                mapOf(
                    "id" to s.id,
                    "title" to s.title,
                    "artistName" to s.artistName,
                    "artistId" to s.artistId,
                    "thumbnailUrl" to s.thumbnailUrl
                )
            }
            
            val docData = mapOf(
                "name" to name,
                "songs" to arr,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            val docRef = FirebaseFirestore.getInstance().collection("shared_playlists").document()
            docRef.set(docData).await()
            
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.arcadesoftware.musix.workers.DeleteSharedPlaylistWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(androidx.work.Data.Builder().putString("docId", docRef.id).build())
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "delete_qr_${docRef.id}",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            val qrData = QR_SCHEME_V4 + docRef.id
            cachedHash = currentHash
            cachedQrData = qrData
            cachedDocId = docRef.id
            return qrData
        } catch (e: Exception) {
            // Fallback to V1 (Compressed Payload) if Firestore fails (e.g. no internet)
            val ids = songs.joinToString(",") { it.id }
            val payload = "n=$name&i=$ids".toByteArray(Charsets.UTF_8)
            
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(payload) }
            val compressed = bos.toByteArray()
            
            val secretKey = javax.crypto.spec.SecretKeySpec(KEY.substring(0, 16).toByteArray(), "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(compressed)
            val qrData = QR_SCHEME_V1 + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            cachedHash = currentHash
            cachedQrData = qrData
            cachedDocId = null
            return qrData
        }
    }

    /** Fetches the playlist from Firestore using the Document ID from the QR, or decodes V1. */
    suspend fun decodePlaylist(raw: String): Triple<String, List<PlayHistoryEntity>, Boolean>? {
        if (raw.startsWith(QR_SCHEME_V4)) {
            return try {
                val docId = raw.removePrefix(QR_SCHEME_V4)
                val doc = FirebaseFirestore.getInstance().collection("shared_playlists").document(docId).get().await()
                
                if (!doc.exists()) return null
                
                val name = doc.getString("name") ?: "Shared Playlist"
                val songsList = doc.get("songs") as? List<Map<String, Any>> ?: emptyList()
                
                val songs = songsList.map { songMap ->
                    PlayHistoryEntity(
                        id = songMap["id"] as? String ?: "",
                        title = songMap["title"] as? String ?: "Unknown",
                        artistName = songMap["artistName"] as? String ?: "Unknown",
                        artistId = songMap["artistId"] as? String,
                        thumbnailUrl = songMap["thumbnailUrl"] as? String ?: ""
                    )
                }
                Triple(name, songs, false)
            } catch (e: Exception) {
                null
            }
        } else if (raw.startsWith(QR_SCHEME_V1)) {
            return try {
                val dataStr = raw.removePrefix(QR_SCHEME_V1)
                val encrypted = Base64.decode(dataStr, Base64.NO_WRAP)
                
                val secretKey = javax.crypto.spec.SecretKeySpec(KEY.substring(0, 16).toByteArray(), "AES")
                val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey)
                val compressed = cipher.doFinal(encrypted)
                
                val bis = java.io.ByteArrayInputStream(compressed)
                val uncompressed = GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { it.readText() }
                
                val parts = uncompressed.split("&")
                val namePart = parts.firstOrNull { it.startsWith("n=") }?.substring(2) ?: "Shared Playlist"
                val idsPart = parts.firstOrNull { it.startsWith("i=") }?.substring(2) ?: ""
                val ids = idsPart.split(",").filter { it.isNotBlank() }
                
                val songs = ids.map { id ->
                    PlayHistoryEntity(
                        id = id,
                        title = "Imported Song",
                        artistName = "Unknown Artist",
                        artistId = null,
                        thumbnailUrl = ""
                    )
                }
                Triple(namePart, songs, false)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}

// ── Gradient QR renderer ──────────────────────────────────────────────────────

@Composable
fun GradientQRCode(
    data: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color.White,
    secondaryColor: Color = Color(0xFFCCCCFF)
) {
    var bitMatrix by remember { mutableStateOf<com.google.zxing.common.BitMatrix?>(null) }

    LaunchedEffect(data) {
        bitMatrix = null
        withContext(Dispatchers.IO) {
            try {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                    EncodeHintType.MARGIN to 1
                )
                // Use 0, 0 to get the unscaled matrix, which is much more efficient to draw
                bitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, hints)
            } catch (_: Exception) {
                bitMatrix = null
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitMatrix == null) {
            CircularProgressIndicator(color = primaryColor, strokeWidth = 2.dp)
        }
        AnimatedVisibility(
            visible = bitMatrix != null,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = FastOutSlowInEasing))
        ) {
            bitMatrix?.let { matrix ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cW = size.width / matrix.width
                    val cH = size.height / matrix.height
                    // Scale down the corner radius so it doesn't turn into full circles
                    val radius = CornerRadius(cW / 3.5f, cH / 3.5f)
                    val brush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    for (x in 0 until matrix.width)
                        for (y in 0 until matrix.height)
                            if (matrix[x, y])
                                drawRoundRect(
                                    brush = brush,
                                    topLeft = Offset(x * cW, y * cH),
                                    size = Size(cW, cH),
                                    cornerRadius = radius
                                )
                }
            }
        }
    }
}

// ── Bottom sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistQRSheet(
    playlistName: String,
    songs: List<PlayHistoryEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var qrData by remember { mutableStateOf<String?>(null) }
    var isRateLimited by remember { mutableStateOf(false) }
    var isExpired by remember { mutableStateOf(false) }

    LaunchedEffect(playlistName, songs) {
        qrData = null
        isRateLimited = false
        isExpired = false
        withContext(Dispatchers.IO) {
            try {
                qrData = PlaylistQRCoder.encodePlaylist(context, playlistName, songs)
            } catch (e: PlaylistQRCoder.RateLimitException) {
                isRateLimited = true
            }
        }
    }
    
    var timeLeft by remember { mutableStateOf(60f) }
    LaunchedEffect(qrData) {
        if (qrData != null) {
            timeLeft = 60f
            isExpired = false
            while (timeLeft > 0) {
                kotlinx.coroutines.delay(100)
                timeLeft -= 0.1f
            }
            timeLeft = 0f
            isExpired = true
            kotlinx.coroutines.delay(1200)
            sheetState.hide()
            onDismiss()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "qr_glow")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "border_rotation"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Share Playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "\"$playlistName\"  •  ${songs.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            val baseModifier = Modifier.size(260.dp)
            val glowModifier = if (isRateLimited) baseModifier else baseModifier.rotatingGlowBorder(rotation = rotation, strokeWidth = 3.dp, cornerRadius = 24.dp)
            
            Box(
                modifier = glowModifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFF141416))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isRateLimited -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Rate Limit Exceeded", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text("Please wait 10 minutes", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    qrData != null -> {
                        Box(contentAlignment = Alignment.Center) {
                            GradientQRCode(
                                data = qrData!!,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    alpha = if (isExpired) 0.2f else 1f
                                }
                            )
                            if (isExpired) {
                                Text(
                                    "QR Expired", 
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    else -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (qrData != null) {
                val progress = timeLeft / 60f
                val progressColor = when {
                    timeLeft > 45 -> Color(0xFF4CAF50) // Green
                    timeLeft > 30 -> Color(0xFFFFEB3B)  // Yellow
                    timeLeft > 15 -> Color(0xFFFF9800)  // Orange
                    else -> MaterialTheme.colorScheme.error // Red
                }
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val timeColor = if (timeLeft <= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                
                val minutes = (timeLeft / 60).toInt()
                val seconds = (timeLeft % 60).toInt()
                val timeString = String.format("%d:%02d", minutes, seconds)
                
                val isBlinking = timeLeft <= 15
                val textAlpha = if (isBlinking && (timeLeft % 1f) < 0.5f) 0.3f else 1f
                
                Text(
                    if (isExpired) "QR Code expired" else "QR Code expires in $timeString",
                    style = MaterialTheme.typography.labelMedium,
                    color = timeColor.copy(alpha = textAlpha),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "Scan with Musix to import this playlist",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
