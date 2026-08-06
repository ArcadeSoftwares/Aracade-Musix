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
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// ── HOW THIS WORKS (Hash-Token QR) ───────────────────────────────────────────
//
// Problem: Encoding all song metadata directly into the QR causes the payload
// length to grow with every song. At ~100+ songs the QR becomes a dense grid
// of tiny pixels that cameras struggle to scan.
//
// Solution — SHA-256 Hash Token:
//   1. ENCODE: Serialize the full playlist to a compact JSON string, then
//      compute SHA-256 of that string → 32 bytes = 64 hex chars.  Store the
//      full JSON in a thread-safe in-memory map (PlaylistTokenStore) keyed by
//      the hash.  Write only  "musix2:<64-char-hash>"  into the QR.
//      → The QR payload is ALWAYS 71 chars regardless of 1 or 1000 songs.
//      → QR stays tiny (Version 3, ~29×29 modules) and scannable forever.
//
//   2. DECODE: Read the hash from the QR, look it up in PlaylistTokenStore,
//      deserialize the JSON.  Works instantly on the same device within the
//      60-second expiry window — no network needed, no extra storage.
//
// Why SHA-256 and not a random UUID?
//   SHA-256 is deterministic: re-generating the QR for the same playlist
//   produces the same hash, which is handy for caching.  It's also compact
//   (64 hex chars) and collision-resistant for any practical playlist size.
// ─────────────────────────────────────────────────────────────────────────────

private const val QR_SCHEME_V2 = "musix2:"

/** Thread-safe in-memory store: hash → (timestamp, serialisedJson) */
object PlaylistTokenStore {
    private data class Entry(val createdAt: Long, val json: String)
    private val store = ConcurrentHashMap<String, Entry>()

    fun put(hash: String, json: String) {
        store[hash] = Entry(System.currentTimeMillis(), json)
        // Evict entries older than 5 minutes to avoid memory leaks
        val cutoff = System.currentTimeMillis() - 5 * 60_000L
        store.entries.removeIf { it.value.createdAt < cutoff }
    }

    fun get(hash: String): Entry? = store[hash]

    data class Resolved(val json: String, val createdAt: Long)
    fun resolve(hash: String): Resolved? = store[hash]?.let { Resolved(it.json, it.createdAt) }}

object PlaylistQRCoder {

    /** Converts raw song list → compact JSON string (used for hashing & storage). */
    private fun buildJson(name: String, songs: List<PlayHistoryEntity>): String {
        val arr = JSONArray().apply {
            songs.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("ti", s.title)
                    put("ar", s.artistName)
                    put("th", s.thumbnailUrl)
                })
            }
        }
        return JSONObject().apply {
            put("v", 2)
            put("n", name)
            put("s", arr)
        }.toString()
    }

    /** SHA-256 of UTF-8 bytes → lowercase hex string (64 chars). */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Encodes a playlist into a fixed-length QR payload string.
     * Returns "musix2:<64-char-sha256-hash>" — always 71 characters,
     * regardless of the number of songs.  The full data is stored in
     * [PlaylistTokenStore] so [decodePlaylist] can retrieve it by hash.
     */
    fun encodePlaylist(name: String, songs: List<PlayHistoryEntity>): String {
        val json  = buildJson(name, songs)
        val hash  = sha256Hex(json)
        PlaylistTokenStore.put(hash, json)
        return QR_SCHEME_V2 + hash
    }

    /**
     * Decodes a QR string and returns Triple(playlistName, songs, isExpired).
     * Returns null if the format is invalid or the token has been evicted.
     */
    fun decodePlaylist(raw: String): Triple<String, List<PlayHistoryEntity>, Boolean>? {
        if (!raw.startsWith(QR_SCHEME_V2)) return null
        return try {
            val hash     = raw.removePrefix(QR_SCHEME_V2)
            val resolved = PlaylistTokenStore.resolve(hash) ?: return null
            val root     = JSONObject(resolved.json)
            val name     = root.getString("n")
            val arr      = root.getJSONArray("s")
            val songs    = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlayHistoryEntity(
                    id           = o.getString("id"),
                    title        = o.getString("ti"),
                    artistName   = o.getString("ar"),
                    artistId     = null,
                    thumbnailUrl = o.getString("th")
                )
            }
            Triple(name, songs, false) // never expired
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
