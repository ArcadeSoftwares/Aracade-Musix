package com.arcadesoftware.musix

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.arcadesoftware.musix.ui.theme.MusixTheme
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import io.github.robinpcrd.cupertino.CupertinoActivityIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure PlayerManager is always initialized, even if MainActivity was never opened
        PlayerManager.init(applicationContext)
        setContent {
            MusixTheme {
                SearchScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val backdrop = rememberLayerBackdrop()

    val searchSongs: (String) -> Unit = { searchQuery ->
        if (searchQuery.isNotBlank()) {
            focusManager.clearFocus()
            isLoading = true
            scope.launch(Dispatchers.IO) {
                val searchResult = YouTube.search(searchQuery, com.music.innertube.YouTube.SearchFilter.FILTER_SONG)
                withContext(Dispatchers.Main) {
                    searchResult.onSuccess { result ->
                        results = result.items.filterIsInstance<SongItem>()
                    }.onFailure { e ->
                        results = emptyList()
                    }
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                YouTube.searchSuggestions(query).onSuccess { suggestResult ->
                    withContext(Dispatchers.Main) {
                        suggestions = suggestResult.queries
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        suggestions = emptyList()
                    }
                }
            }
        } else {
            suggestions = emptyList()
        }
        results = emptyList()
    }

    // Outer container — Both MiniPlayer AND Top Bar must be SIBLINGS of the layerBackdrop box,
    // not children inside it. This architecture avoids circular GPU rendering (SIGSEGV).
    Box(modifier = Modifier.fillMaxSize()) {
        // Source box: layerBackdrop captures ONLY this layer as the GPU source
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentSong != null) 80.dp else 0.dp)
            ) {
                // Spacer for the floating Top Bar (statusBarsPadding + Row height + vertical padding)
                // height is 56.dp + 12.dp * 2 = 80.dp total area occupied by Top Bar.
                Spacer(modifier = Modifier.statusBarsPadding().height(80.dp))

                // Results / Suggestions List
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CupertinoActivityIndicator(modifier = Modifier.padding(16.dp))
                    }
                } else if (results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(results) { song ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                SearchSongRow(
                                    song = song,
                                    onClick = { PlayerManager.play(song) }
                                )
                            }
                        }
                    }
                } else if (query.isNotEmpty() && suggestions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.2f))
                                    .clickable {
                                        query = suggestion
                                        searchSongs(suggestion)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (query.isEmpty()) "Search for songs" else "No results found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Top Bar with Search Input — Overlays on top of the content box
        // and reads the GPU snapshot of the content box behind it via drawBackdrop().
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(28.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                placeholder = { Text("Search songs...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { searchSongs(query) }
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
        }

        // MiniPlayer is a sibling OUTSIDE the layerBackdrop box — it overlays on top
        // and reads the GPU snapshot of the content box behind it via drawBackdrop().
        // This matches the MainScreen pattern and eliminates the circular GPU SIGSEGV.
        androidx.compose.animation.AnimatedVisibility(
            visible = currentSong != null,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            MiniPlayer(
                backdrop = backdrop,
                currentSong = currentSong,
                collapsedBottomPadding = 16.dp
            )
        }
    }
}

@Composable
fun SearchSongRow(
    song: SongItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(0.2f))
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artists?.joinToString { it.name } ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = Color(0xFFFA243C), // Apple Music Red
            modifier = Modifier.padding(12.dp)
        )
    }
}
