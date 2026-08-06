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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

// ── HOW THIS WORKS (V3 QR) ───────────────────────────────────────────────────
//
// Hash-tokens (V2) failed for cross-device sharing since the data was only 
// in-memory on the sender device. To fix this, we must embed the data in the QR.
// To prevent the QR from becoming too dense (unscannable) for 100+ songs, we:
//   1. Drop thumbnailUrl (the longest string) - receivers will lazy-load it
//   2. Use a dense JSON Array of Arrays (not Objects with keys)
//   3. GZIP compress the JSON string
//   4. Base64 URL-safe encode it
// ─────────────────────────────────────────────────────────────────────────────

private const val QR_SCHEME_V3 = "musix3:"

object PlaylistQRCoder {

    /** Converts raw song list → compact JSON string. */
    private fun buildJson(name: String, songs: List<PlayHistoryEntity>): String {
        val arr = JSONArray().apply {
            songs.forEach { s ->
                // Array instead of object to save bytes: [id, title, artistName]
                put(JSONArray().apply {
                    put(s.id)
                    put(s.title)
                    put(s.artistName)
                })
            }
        }
        return JSONObject().apply {
            put("v", 3)
            put("n", name)
            put("s", arr)
        }.toString()
    }

    /** Encodes playlist using GZIP and Base64. */
    fun encodePlaylist(name: String, songs: List<PlayHistoryEntity>): String {
        val json = buildJson(name, songs)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        return QR_SCHEME_V3 + b64
    }

    /** Decodes the Base64 GZIP string back into entities. */
    fun decodePlaylist(raw: String): Triple<String, List<PlayHistoryEntity>, Boolean>? {
        if (!raw.startsWith(QR_SCHEME_V3)) return null
        return try {
            val b64 = raw.removePrefix(QR_SCHEME_V3)
            val bytes = Base64.decode(b64, Base64.NO_WRAP or Base64.URL_SAFE)
            val json = GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).readText()
            
            val root = JSONObject(json)
            val name = root.getString("n")
            val arr = root.getJSONArray("s")
            
            val songs = (0 until arr.length()).map { i ->
                val songArr = arr.getJSONArray(i)
                PlayHistoryEntity(
                    id = songArr.getString(0),
                    title = songArr.getString(1),
                    artistName = songArr.getString(2),
                    artistId = null,
                    thumbnailUrl = "" // Dropped to save space
                )
            }
            Triple(name, songs, false)
        } catch (e: Exception) {
            null
        }
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
                bitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
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
                    val radius = CornerRadius(cW / 2f, cH / 2f)
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

    var qrData by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistName, songs) {
        qrData = null
        withContext(Dispatchers.IO) {
            qrData = PlaylistQRCoder.encodePlaylist(playlistName, songs)
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

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .rotatingGlowBorder(rotation = rotation, strokeWidth = 3.dp, cornerRadius = 24.dp)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFF141416))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qrData != null -> GradientQRCode(
                        data = qrData!!,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
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
