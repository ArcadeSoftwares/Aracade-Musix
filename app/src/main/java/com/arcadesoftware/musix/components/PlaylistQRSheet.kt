package com.arcadesoftware.musix.components

import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import com.arcadesoftware.musix.db.entities.PlayHistoryEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object PlaylistQRCoder {
    private const val KEY = "MusixAppShareKey1234567890123456"
    
    fun encodePlaylist(name: String, songs: List<PlayHistoryEntity>): String {
        val ids = songs.joinToString(",") { it.id }
        val payload = "n=$name&i=$ids".toByteArray(Charsets.UTF_8)
        
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bos).use { it.write(payload) }
        val compressed = bos.toByteArray()
        
        val secretKey = SecretKeySpec(KEY.substring(0, 16).toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(compressed)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}

@Composable
fun GradientQRCode(
    data: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFFFA243C),
    secondaryColor: Color = Color(0xFFFF7E5F)
) {
    var bitMatrix by remember { mutableStateOf<com.google.zxing.common.BitMatrix?>(null) }

    LaunchedEffect(data) {
        withContext(Dispatchers.IO) {
            try {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                    EncodeHintType.MARGIN to 1
                )
                bitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
            } catch (e: Exception) {
                bitMatrix = null
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitMatrix == null) {
            CircularProgressIndicator(color = primaryColor)
        }
        
        AnimatedVisibility(
            visible = bitMatrix != null,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = FastOutSlowInEasing))
        ) {
            bitMatrix?.let { matrix ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val matrixWidth = matrix.width
                    val matrixHeight = matrix.height
                    val cellWidth = width / matrixWidth
                    val cellHeight = height / matrixHeight
                    val cornerRadius = CornerRadius(cellWidth / 1.8f, cellHeight / 1.8f)

                    val brush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor),
                        start = Offset(0f, 0f),
                        end = Offset(width, height)
                    )

                    for (x in 0 until matrixWidth) {
                        for (y in 0 until matrixHeight) {
                            if (matrix.get(x, y)) {
                                drawRoundRect(
                                    brush = brush,
                                    topLeft = Offset(x * cellWidth, y * cellHeight),
                                    size = Size(cellWidth, cellHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
        withContext(Dispatchers.IO) {
            qrData = PlaylistQRCoder.encodePlaylist(playlistName, songs)
        }
    }

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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Scan to import '${playlistName}'",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFA243C), Color(0xFFFF5252))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrData != null) {
                    GradientQRCode(
                        data = qrData!!,
                        modifier = Modifier.fillMaxSize(),
                        primaryColor = Color(0xFFFA243C),
                        secondaryColor = Color(0xFFFF5252)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Contains ${songs.size} songs",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
