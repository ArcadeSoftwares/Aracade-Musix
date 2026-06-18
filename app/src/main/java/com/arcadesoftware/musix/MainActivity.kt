package com.arcadesoftware.musix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadesoftware.musix.ui.theme.MusixTheme
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.arcadesoftware.musix.ui.screens.HomeScreen
import com.arcadesoftware.musix.updater.MusixUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import com.music.innertube.models.YTItem
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.music.innertube.models.*
import com.music.innertube.YouTube
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlayerManager {
    private const val ACTION_PLAY = "com.arcadesoftware.musix.ACTION_PLAY"
    private const val ACTION_PAUSE = "com.arcadesoftware.musix.ACTION_PAUSE"
    private const val ACTION_PREVIOUS = "com.arcadesoftware.musix.ACTION_PREVIOUS"
    private const val ACTION_NEXT = "com.arcadesoftware.musix.ACTION_NEXT"

    val currentSong = MutableStateFlow<YTItem?>(null)
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val currentDuration = MutableStateFlow(0L)
    private var appContext: android.content.Context? = null
    private var mediaSession: android.media.session.MediaSession? = null
    private var lastThumbnailUrl: String? = null
    private var currentMetadataSongId: String? = null
    private var currentMetadataDuration: Long = 0L
    private var currentMetadataBitmap: android.graphics.Bitmap? = null
    var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val TAG = "PlayerManager"

    @Volatile
    var activeUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    // Complete client order mirroring Echo-Music to check all playback possibilities
    private val CLIENTS = arrayOf(
        com.music.innertube.models.YouTubeClient.ANDROID_VR_NO_AUTH,
        com.music.innertube.models.YouTubeClient.ANDROID_VR_1_43_32,
        com.music.innertube.models.YouTubeClient.ANDROID_VR_1_61_48,
        com.music.innertube.models.YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        com.music.innertube.models.YouTubeClient.TVHTML5,
        com.music.innertube.models.YouTubeClient.ANDROID_CREATOR,
        com.music.innertube.models.YouTubeClient.IPADOS,
        com.music.innertube.models.YouTubeClient.IOS,
        com.music.innertube.models.YouTubeClient.WEB,
        com.music.innertube.models.YouTubeClient.WEB_REMIX,
        com.music.innertube.models.YouTubeClient.WEB_CREATOR,
        com.music.innertube.models.YouTubeClient.MOBILE,
        com.music.innertube.models.YouTubeClient.ANDROID_NO_SDK,
    )

    private fun validateUrl(urlStr: String, userAgent: String): Boolean {
        return try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val responseCode = connection.responseCode
            android.util.Log.d(TAG, "Validation response for URL: $responseCode")
            
            // Accept 2xx success codes or 3xx redirects
            responseCode in 200..399
        } catch (e: Exception) {
            android.util.Log.e(TAG, "URL validation failed with exception: ${e.message}")
            false
        }
    }

    fun init(context: android.content.Context) {
        if (exoPlayer == null) {
            appContext = context.applicationContext

            // Initialize platform MediaSession
            val session = android.media.session.MediaSession(context, "MusixPlayer").apply {
                isActive = true
                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onPlay() {
                        exoPlayer?.play()
                    }
                    override fun onPause() {
                        exoPlayer?.pause()
                    }
                    override fun onSkipToNext() {
                        // Skip to next if playlist is active
                    }
                    override fun onSkipToPrevious() {
                        exoPlayer?.seekTo(0)
                    }
                    override fun onSeekTo(pos: Long) {
                        exoPlayer?.seekTo(pos)
                        currentPosition.value = pos
                    }
                })
            }
            mediaSession = session

            // Register receiver for notification actions
            val filter = android.content.IntentFilter().apply {
                addAction(ACTION_PLAY)
                addAction(ACTION_PAUSE)
                addAction(ACTION_PREVIOUS)
                addAction(ACTION_NEXT)
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                    when (intent.action) {
                        ACTION_PLAY -> exoPlayer?.play()
                        ACTION_PAUSE -> exoPlayer?.pause()
                        ACTION_PREVIOUS -> exoPlayer?.seekTo(0)
                        ACTION_NEXT -> {}
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            startProgressUpdates()

            scope.launch {
                if (YouTube.visitorData == null) {
                    android.util.Log.d(TAG, "Initializing guest session visitorData...")
                    YouTube.refreshVisitorData().onSuccess { newData ->
                        android.util.Log.i(TAG, "Initialized guest session visitorData successfully: $newData")
                    }.onFailure { e ->
                        android.util.Log.e(TAG, "Failed to initialize guest session visitorData: ${e.message}", e)
                    }
                }
            }
            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()

            val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
                httpDataSourceFactory
            ) { dataSpec ->
                dataSpec.withAdditionalHeaders(mapOf(
                    "User-Agent" to activeUserAgent,
                    "Referer" to "https://www.youtube.com/"
                ))
            }

            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, resolvingDataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                )
                .build()

            exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying.value = playing
                    updatePlaybackState()
                    showOrUpdateNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateStr = when (playbackState) {
                        androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                        androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                        androidx.media3.common.Player.STATE_READY -> "READY"
                        androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    android.util.Log.d(TAG, "ExoPlayer state changed: $stateStr")
                    updatePlaybackState()
                    showOrUpdateNotification()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                    android.util.Log.e(TAG, "Error code: ${error.errorCodeName} (${error.errorCode})")
                }
            })
        }
    }

    fun play(item: YTItem) {
        scope.launch {
            android.util.Log.d(TAG, "Resolving item: class=${item::class.java.simpleName}, id=${item.id}")
            val resolvedSong = when (item) {
                is SongItem -> item
                is PlaylistItem -> {
                    android.util.Log.d(TAG, "Fetching playlist ${item.id} to play the first song")
                    val result = YouTube.playlist(item.id)
                    var song: SongItem? = null
                    result.onSuccess { playlistPage ->
                        song = playlistPage.songs.firstOrNull()
                    }.onFailure { e ->
                        android.util.Log.e(TAG, "Failed to load playlist: ${e.message}", e)
                    }
                    song
                }
                is AlbumItem -> {
                    android.util.Log.d(TAG, "Fetching album ${item.browseId} to play the first song")
                    val result = YouTube.album(item.browseId)
                    var song: SongItem? = null
                    result.onSuccess { albumPage ->
                        song = albumPage.songs.firstOrNull()
                    }.onFailure { e ->
                        android.util.Log.e(TAG, "Failed to load album: ${e.message}", e)
                    }
                    song
                }
                is ArtistItem -> {
                    android.util.Log.d(TAG, "Resolving artist ${item.id} to play the first song")
                    val endpoint = item.radioEndpoint ?: item.playEndpoint ?: item.shuffleEndpoint
                    if (endpoint != null) {
                        val result = YouTube.next(endpoint)
                        var song: SongItem? = null
                        result.onSuccess { nextResult ->
                            song = nextResult.items.firstOrNull()
                        }.onFailure { e ->
                            android.util.Log.e(TAG, "Failed to load artist nextResult: ${e.message}", e)
                        }
                        song
                    } else {
                        android.util.Log.e(TAG, "Artist has no playable endpoints")
                        null
                    }
                }
                else -> null
            }

            if (resolvedSong == null) {
                android.util.Log.e(TAG, "Could not resolve a song for item: $item")
                return@launch
            }

            withContext(Dispatchers.Main) {
                currentSong.value = resolvedSong
                updatePlaybackDetails()
            }

            val videoId = resolvedSong.id
            android.util.Log.d(TAG, "Playing videoId=$videoId")
            var streamUrl: String? = null
            var usedUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

            suspend fun resolveStream(): Boolean {
                val signatureTimestamp = com.music.innertube.NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                android.util.Log.d(TAG, "Signature timestamp: $signatureTimestamp")

                for (client in CLIENTS) {
                    android.util.Log.d(TAG, "Trying client: ${client.clientName} v${client.clientVersion}")
                    val response = YouTube.player(videoId = videoId, client = client, signatureTimestamp = signatureTimestamp)
                    response.onSuccess { playerResponse ->
                        val status = playerResponse.playabilityStatus.status
                        android.util.Log.d(TAG, "Status: $status, reason: ${playerResponse.playabilityStatus.reason}")

                        if (status != "OK") return@onSuccess

                        // Prefer audio-only adaptive formats, then fall back to combined formats
                        val format = playerResponse.streamingData?.adaptiveFormats
                            ?.filter { it.mimeType.startsWith("audio/") }
                            ?.maxByOrNull { it.bitrate }
                            ?: playerResponse.streamingData?.formats?.firstOrNull()

                        if (format != null) {
                            val url = com.music.innertube.NewPipeExtractor.getStreamUrl(format, videoId)
                            android.util.Log.d(TAG, "Format: ${format.mimeType}, bitrate: ${format.bitrate}, resolved url null? ${url == null}")

                            if (url != null) {
                                val ua = client.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
                                if (validateUrl(url, ua)) {
                                    streamUrl = url
                                    usedUserAgent = ua
                                } else {
                                    android.util.Log.w(TAG, "Skipping client ${client.clientName} due to validation failure (403 or network issue)")
                                }
                            }
                        }
                    }
                    response.onFailure { e ->
                        android.util.Log.e(TAG, "Client ${client.clientName} failed: ${e.message}")
                    }
                    if (streamUrl != null) return true
                }
                return false
            }

            var resolved = resolveStream()
            if (!resolved) {
                android.util.Log.w(TAG, "Playback resolution failed on first attempt. Rotating guest session and retrying...")
                YouTube.visitorData = null
                YouTube.refreshVisitorData().onSuccess { newData ->
                    android.util.Log.i(TAG, "Rotated visitorData successfully: $newData")
                }.onFailure { e ->
                    android.util.Log.e(TAG, "Failed to rotate visitorData: ${e.message}", e)
                }
                resolved = resolveStream()
            }

            if (streamUrl != null) {
                activeUserAgent = usedUserAgent
                android.util.Log.d(TAG, "Playing stream URL (length=${streamUrl!!.length}) with User-Agent: $activeUserAgent")
                withContext(Dispatchers.Main) {
                    exoPlayer?.stop()
                    exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl!!))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    updatePlaybackDetails()
                }
            } else {
                android.util.Log.e(TAG, "All clients failed for videoId=$videoId")
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    private fun startProgressUpdates() {
        scope.launch {
            while (true) {
                withContext(Dispatchers.Main) {
                    exoPlayer?.let { player ->
                        if (player.isPlaying || player.playbackState == androidx.media3.common.Player.STATE_READY) {
                            currentPosition.value = player.currentPosition
                            currentDuration.value = player.duration.coerceAtLeast(0L)
                            updatePlaybackState()
                        }
                    }
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun seekTo(fraction: Float) {
        exoPlayer?.let { player ->
            val duration = player.duration
            if (duration > 0) {
                val position = (duration * fraction).toLong()
                player.seekTo(position)
                currentPosition.value = position
                updatePlaybackState()
            }
        }
    }

    private fun updatePlaybackState() {
        val session = mediaSession ?: return
        val player = exoPlayer ?: return
        val song = currentSong.value as? SongItem ?: return

        val state = if (player.isPlaying) {
            android.media.session.PlaybackState.STATE_PLAYING
        } else {
            android.media.session.PlaybackState.STATE_PAUSED
        }

        // Check if duration has become available or has changed
        val playerDuration = player.duration
        val durationMs = if (playerDuration > 0) playerDuration else 0L
        if (durationMs > 0 && durationMs != currentMetadataDuration && song.id == currentMetadataSongId) {
            currentMetadataDuration = durationMs
            val metadataBuilder = android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, song.title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, song.artists.joinToString { it.name })
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, durationMs)
            
            currentMetadataBitmap?.let { bitmap ->
                metadataBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
            }
            session.setMetadata(metadataBuilder.build())
        }

        val playbackState = android.media.session.PlaybackState.Builder()
            .setState(state, player.currentPosition, 1.0f)
            .setActions(
                android.media.session.PlaybackState.ACTION_PLAY or
                android.media.session.PlaybackState.ACTION_PAUSE or
                android.media.session.PlaybackState.ACTION_SEEK_TO or
                android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .build()

        session.setPlaybackState(playbackState)
    }

    private fun updatePlaybackDetails() {
        val song = currentSong.value as? SongItem ?: return

        val session = mediaSession ?: return
        val durationMs = exoPlayer?.duration ?: 0L
        val songDuration = song.duration
        val targetDuration = if (durationMs > 0) {
            durationMs
        } else if (songDuration != null) {
            songDuration * 1000L
        } else {
            0L
        }

        currentMetadataSongId = song.id
        currentMetadataDuration = targetDuration
        currentMetadataBitmap = null // Reset bitmap for new song

        val metadataBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, song.title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, song.artists.joinToString { it.name })
        if (targetDuration > 0) {
            metadataBuilder.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, targetDuration)
        }
        session.setMetadata(metadataBuilder.build())

        updatePlaybackState()
        showOrUpdateNotification()

        if (song.thumbnail != lastThumbnailUrl) {
            lastThumbnailUrl = song.thumbnail
            scope.launch {
                val context = appContext ?: return@launch
                try {
                    val loader = coil.ImageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(song.thumbnail)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    val drawable = result.drawable
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        val bitmap = drawable.bitmap
                        withContext(Dispatchers.Main) {
                            val activeSong = currentSong.value as? SongItem
                            if (activeSong?.id == song.id) {
                                currentMetadataBitmap = bitmap
                                val meta = android.media.MediaMetadata.Builder()
                                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, song.title)
                                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, song.artists.joinToString { it.name })
                                    .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                                if (currentMetadataDuration > 0) {
                                    meta.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, currentMetadataDuration)
                                }
                                session.setMetadata(meta.build())
                                showOrUpdateNotification(bitmap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to load notification artwork: ${e.message}")
                }
            }
        }
    }

    private fun showOrUpdateNotification(largeIcon: android.graphics.Bitmap? = null) {
        val context = appContext ?: return
        val song = currentSong.value as? SongItem ?: return
        val isPlayingVal = isPlaying.value

        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "music_playback",
                "Music Playback",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val playIntent = android.app.PendingIntent.getBroadcast(
            context, 0, android.content.Intent(ACTION_PLAY),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = android.app.PendingIntent.getBroadcast(
            context, 1, android.content.Intent(ACTION_PAUSE),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = android.app.PendingIntent.getBroadcast(
            context, 2, android.content.Intent(ACTION_PREVIOUS),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = android.app.PendingIntent.getBroadcast(
            context, 3, android.content.Intent(ACTION_NEXT),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context, 4, mainActivityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, "music_playback")
        } else {
            android.app.Notification.Builder(context)
        }

        builder.setContentTitle(song.title)
            .setContentText(song.artists.joinToString { it.name })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlayingVal)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        builder.addAction(
            android.app.Notification.Action.Builder(
                android.R.drawable.ic_media_previous, "Previous", prevIntent
            ).build()
        )
        if (isPlayingVal) {
            builder.addAction(
                android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_pause, "Pause", pauseIntent
                ).build()
            )
        } else {
            builder.addAction(
                android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_play, "Play", playIntent
                ).build()
            )
        }
        builder.addAction(
            android.app.Notification.Action.Builder(
                android.R.drawable.ic_media_next, "Next", nextIntent
            ).build()
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val style = android.app.Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
            mediaSession?.let {
                style.setMediaSession(it.sessionToken)
            }
            builder.setStyle(style)
        }

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
            ) {
                notificationManager.notify(1001, builder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show notification: ${e.message}")
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusixTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val currentSong by PlayerManager.currentSong.collectAsState()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MainScreen", "POST_NOTIFICATIONS permission granted: $isGranted")
    }
    
    LaunchedEffect(Unit) {
        PlayerManager.init(context.applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        MusixUpdater.checkForUpdate(
            context = context,
            onUpdateFound = { version, description, apkUrl ->
//                Toast.makeText(context, "Update v$version available!", Toast.LENGTH_LONG).show()
            },
            onNoUpdate = {}
        )
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { 
                    selectedTab = it
                },
                onSearchClick = { 
                    context.startActivity(android.content.Intent(context, SearchActivity::class.java))
                },
                backdrop = backdrop
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> Text("Playlist Fragment")
                2 -> Text("Library Fragment")
                3 -> Text("Podcast Fragment")
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = currentSong != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        ) {
            MiniPlayer(backdrop = backdrop, currentSong = currentSong)
        }
    }
}

@Composable
fun AppBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop
) {
    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    val activeColor = Color(0xFFFA243C) // Apple Music Red
    val inactiveColor = if (isLightTheme) Color.Black else Color.White
    val containerColor = if (isLightTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        com.arcadesoftware.musix.components.LiquidBottomTabs(
            selectedTabIndex = selectedTab,
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = 4,
            accentColor = activeColor,
            modifier = Modifier.weight(1f)
        ) {
            LiquidBottomTab(onClick = { onTabSelected(0) }) {
                Icon(
                    Icons.Rounded.Home, 
                    contentDescription = "Home", 
                    tint = if (selectedTab == 0) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Home", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (selectedTab == 0) activeColor else inactiveColor
                )
            }
            LiquidBottomTab(onClick = { onTabSelected(1) }) {
                Icon(
                    Icons.Rounded.QueueMusic, 
                    contentDescription = "Playlist", 
                    tint = if (selectedTab == 1) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Playlist", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (selectedTab == 1) activeColor else inactiveColor
                )
            }
            LiquidBottomTab(onClick = { onTabSelected(2) }) {
                Icon(
                    Icons.Rounded.LibraryMusic, 
                    contentDescription = "Library", 
                    tint = if (selectedTab == 2) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Library", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (selectedTab == 2) activeColor else inactiveColor
                )
            }
            LiquidBottomTab(onClick = { onTabSelected(3) }) {
                Icon(
                    Icons.Rounded.Podcasts, 
                    contentDescription = "Podcast", 
                    tint = if (selectedTab == 3) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Podcast", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (selectedTab == 3) activeColor else inactiveColor
                )
            }
        }
        
        com.arcadesoftware.musix.components.LiquidButton(
            onClick = onSearchClick,
            backdrop = backdrop,
            surfaceColor = containerColor,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                Icons.Rounded.Search, 
                contentDescription = "Search", 
                tint = inactiveColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun MiniPlayer(
    backdrop: com.kyant.backdrop.Backdrop,
    currentSong: YTItem?,
    modifier: Modifier = Modifier,
    collapsedBottomPadding: androidx.compose.ui.unit.Dp = 112.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val currentAlpha by androidx.compose.animation.core.animateFloatAsState(if (expanded) 0.85f else (if (isLightTheme) 0.5f else 0.4f))
    val containerColor = if (isLightTheme) Color.White.copy(alpha = currentAlpha) else Color.Black.copy(alpha = currentAlpha)
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val playPauseIcon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

    val title = when (currentSong) {
        is SongItem -> currentSong.title
        is AlbumItem -> currentSong.title
        is PlaylistItem -> currentSong.title
        is ArtistItem -> currentSong.title
        else -> "Unknown Title"
    }
    val subtitle = when (currentSong) {
        is SongItem -> currentSong.artists?.joinToString { it.name } ?: "Unknown Artist"
        is AlbumItem -> currentSong.artists?.joinToString { it.name } ?: "Unknown Artist"
        is PlaylistItem -> currentSong.author?.name ?: "Playlist"
        is ArtistItem -> "Artist"
        else -> "Unknown Artist"
    }
    val thumbnail = when (currentSong) {
        is SongItem -> currentSong.thumbnail
        is AlbumItem -> currentSong.thumbnail
        is PlaylistItem -> currentSong.thumbnail
        is ArtistItem -> currentSong.thumbnail
        else -> ""
    }

    val bottomPadding by androidx.compose.animation.core.animateDpAsState(if (expanded) 0.dp else collapsedBottomPadding)
    val horizontalPadding by androidx.compose.animation.core.animateDpAsState(if (expanded) 0.dp else 45.dp)
    val cornerRadius by androidx.compose.animation.core.animateDpAsState(if (expanded) 0.dp else 100.dp)
    val currentBlur by androidx.compose.animation.core.animateDpAsState(if (expanded) 8.dp else 4.dp)

    com.arcadesoftware.musix.components.LiquidButton(
        onClick = { expanded = !expanded },
        backdrop = backdrop,
        surfaceColor = containerColor,
        blurRadius = currentBlur,
        isInteractive = false,
        shape = { RoundedCornerShape(cornerRadius) },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding, start = horizontalPadding, end = horizontalPadding)
            .animateContentSize(animationSpec = spring())
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < -10) expanded = true
                        else if (dragAmount > 10) expanded = false
                    }
                )
            }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val consumeClicksModifier = Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {}
            if (!expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Gray.copy(0.5f))) {
                        AsyncImage(model = thumbnail ?: "", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(subtitle, color = contentColor.copy(0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = contentColor, modifier = Modifier.size(20.dp).then(consumeClicksModifier))
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(playPauseIcon, contentDescription = "Play/Pause", tint = contentColor, modifier = Modifier.size(24.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { PlayerManager.togglePlayPause() })
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = contentColor, modifier = Modifier.size(20.dp).then(consumeClicksModifier))
                    Spacer(modifier = Modifier.width(4.dp))
                }
            } else {
                val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                Column(
                    modifier = Modifier.fillMaxWidth().height(screenHeight).padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(0.5f))) {
                        AsyncImage(model = thumbnail ?: "", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(title, color = contentColor, style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(subtitle, color = contentColor.copy(0.7f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Like", tint = contentColor, modifier = Modifier.size(32.dp).then(consumeClicksModifier))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
                    val currentPosition by PlayerManager.currentPosition.collectAsState()
                    val currentDuration by PlayerManager.currentDuration.collectAsState()
                    val sliderValue = sliderDragValue ?: (if (currentDuration > 0) currentPosition.toFloat() / currentDuration else 0f)

                    val sliderConsumeGesture = Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures { _, _ -> }
                    }
                    com.arcadesoftware.musix.components.LiquidSlider(
                        value = { sliderValue },
                        onValueChange = { sliderDragValue = it },
                        onValueChangeFinished = {
                            sliderDragValue?.let { PlayerManager.seekTo(it) }
                            sliderDragValue = null
                        },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop,
                        accentColor = contentColor,
                        modifier = Modifier.padding(horizontal = 16.dp).then(sliderConsumeGesture)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = contentColor, modifier = Modifier.size(48.dp).then(consumeClicksModifier))
                        Icon(playPauseIcon, contentDescription = "Play/Pause", tint = contentColor, modifier = Modifier.size(72.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { PlayerManager.togglePlayPause() })
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = contentColor, modifier = Modifier.size(48.dp).then(consumeClicksModifier))
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Add to Playlist", tint = contentColor.copy(0.8f), modifier = Modifier.size(28.dp).then(consumeClicksModifier))
                        Icon(Icons.Rounded.Download, contentDescription = "Download", tint = contentColor.copy(0.8f), modifier = Modifier.size(28.dp).then(consumeClicksModifier))
                        Icon(Icons.Rounded.Lyrics, contentDescription = "Lyrics", tint = contentColor.copy(0.8f), modifier = Modifier.size(28.dp).then(consumeClicksModifier))
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Up Next", tint = contentColor.copy(0.8f), modifier = Modifier.size(28.dp).then(consumeClicksModifier))
                    }
                }
            }
        }
    }
}
