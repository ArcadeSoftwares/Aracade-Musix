package com.arcadesoftware.musix.ui.screens

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.arcadesoftware.musix.PlayerManager
import com.arcadesoftware.musix.db.AppDatabase
import com.arcadesoftware.musix.db.entities.DownloadedSongEntity
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private val AccentRed = Color(0xFFFA243C)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val downloadedSongs: StateFlow<List<DownloadedSongEntity>> = db.musicDao().getDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

private data class DownloadGroup(
    val name: String,
    val songs: List<Pair<String, SongItem>>,
    val coverUrl: String?
)

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel(), onBackClick: (() -> Unit)? = null) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadProgressMap by PlayerManager.downloadProgressMap.collectAsState()
    val downloadDetailsMap by PlayerManager.downloadDetailsMap.collectAsState()
    val downloadPauseMap by PlayerManager.downloadPauseMap.collectAsState()
    val downloadGroupMap by PlayerManager.downloadGroupMap.collectAsState()

    val activeQueue = remember(downloadProgressMap) { downloadProgressMap.filter { it.value < 1.0f } }

    val groups: List<DownloadGroup> = remember(activeQueue, downloadDetailsMap, downloadGroupMap) {
        activeQueue.keys
            .groupBy { songId -> downloadGroupMap[songId] ?: "Singles" }
            .map { (groupName, songIds) ->
                val songs = songIds.mapNotNull { id -> downloadDetailsMap[id]?.let { id to it } }
                DownloadGroup(
                    name = groupName,
                    songs = songs,
                    coverUrl = songs.firstOrNull()?.second?.thumbnail
                )
            }
            .sortedBy { it.name }
    }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = topPadding, bottom = 140.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Spacer(Modifier.width(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Center",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (activeQueue.isNotEmpty()) {
                        Text(
                            text = "${activeQueue.size} song${if (activeQueue.size > 1) "s" else ""} downloading",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (activeQueue.isNotEmpty()) {
                    TextButton(
                        onClick = { PlayerManager.cancelAllDownloads() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel all", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Active Downloads Section
        if (groups.isNotEmpty()) {
            item {
                Text(
                    text = "DOWNLOADING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            items(groups, key = { it.name }) { group ->
                val isExpanded = expandedGroups[group.name] ?: true
                val chevronAngle by animateFloatAsState(
                    targetValue = if (isExpanded) 0f else -90f, label = "chevron"
                )
                val groupProgress = remember(group, downloadProgressMap) {
                    if (group.songs.isEmpty()) 0f
                    else group.songs.map { (id, _) -> downloadProgressMap[id] ?: 0f }.average().toFloat()
                }
                val allPaused = remember(group, downloadPauseMap) {
                    group.songs.isNotEmpty() && group.songs.all { (id, _) -> downloadPauseMap[id] == true }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column {
                        // Group header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedGroups[group.name] = !(expandedGroups[group.name] ?: true) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cover + ring
                            Box(
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(0.12f))
                            ) {
                                if (!group.coverUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = group.coverUrl, contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Center).size(24.dp))
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { groupProgress },
                                        modifier = Modifier.size(34.dp),
                                        strokeWidth = 3.dp,
                                        color = if (allPaused) Color.Gray else AccentRed,
                                        trackColor = Color.White.copy(0.18f)
                                    )
                                    Text(
                                        "${(groupProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${group.songs.size} track${if (group.songs.size > 1) "s" else ""}  •  ${(groupProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f)
                                )
                            }
                            // Pause/Resume all
                            IconButton(
                                onClick = {
                                    if (allPaused) PlayerManager.resumeAllInGroup(group.name)
                                    else PlayerManager.pauseAllInGroup(group.name)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (allPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = if (allPaused) "Resume all" else "Pause all",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Cancel all in group
                            IconButton(
                                onClick = { PlayerManager.cancelAllDownloads(group.name) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel all",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                            }
                            // Expand chevron
                            Icon(Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                                modifier = Modifier.size(22.dp).rotate(chevronAngle))
                        }

                        // Progress stripe
                        LinearProgressIndicator(
                            progress = { groupProgress },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = if (allPaused) Color.Gray else AccentRed,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.07f)
                        )

                        // Song rows
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                group.songs.forEach { (songId, details) ->
                                    val progress = downloadProgressMap[songId] ?: 0f
                                    val isPaused = downloadPauseMap[songId] ?: false
                                    DownloadSongRow(songId, details, progress, isPaused)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // Empty state
        if (downloadedSongs.isEmpty() && activeQueue.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f),
                            modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("No Downloads", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    Spacer(Modifier.height(6.dp))
                    Text("Songs you download will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.32f))
                }
            }
        }

        // Downloaded Songs
        if (downloadedSongs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DOWNLOADED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${downloadedSongs.size} song${if (downloadedSongs.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                    )
                }
            }

            items(downloadedSongs, key = { it.id }) { songEntity ->
                val songItem = SongItem(
                    id = songEntity.id,
                    title = songEntity.title,
                    artists = listOf(Artist(name = songEntity.artistName, id = songEntity.artistId)),
                    thumbnail = songEntity.thumbnailUrl,
                    explicit = false
                )
                val isCurrentlyPlaying = PlayerManager.currentSong.collectAsState().value?.id == songEntity.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerManager.play(songItem) }
                        .background(
                            if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary.copy(0.07f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = songEntity.thumbnailUrl, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        if (isCurrentlyPlaying) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songEntity.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCurrentlyPlaying) AccentRed else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = songEntity.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f),
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                            .background(Color(0xFF22C55E).copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Downloaded",
                            tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSongRow(songId: String, details: SongItem, progress: Float, isPaused: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 80.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!details.thumbnail.isNullOrEmpty()) {
                AsyncImage(model = details.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = details.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (isPaused) Color.Gray else AccentRed,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(0.09f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (isPaused) "Paused" else "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isPaused) Color.Gray else AccentRed,
            modifier = Modifier.width(40.dp)
        )
        IconButton(
            onClick = { PlayerManager.togglePauseDownload(songId) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(
            onClick = { PlayerManager.cancelDownload(songId) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
    }
}
