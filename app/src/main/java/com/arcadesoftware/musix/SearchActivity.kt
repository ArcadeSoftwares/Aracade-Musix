package com.arcadesoftware.musix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.arcadesoftware.musix.ui.theme.MusixTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import io.github.robinpcrd.cupertino.CupertinoActivityIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        PlayerManager.init(applicationContext)
        setContent {
            val sharedPrefs = getSharedPreferences("musix_profile_settings", android.content.Context.MODE_PRIVATE)
            val themePref = sharedPrefs.getInt("theme_preference", 0)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }
            MusixTheme(darkTheme = darkTheme) {
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
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    
    var searchHistoryList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAddToPlaylistForSong by remember { mutableStateOf<SongItem?>(null) }
    
    val loadSearchHistory = {
        val prefs = context.getSharedPreferences("search_cache_prefs", android.content.Context.MODE_PRIVATE)
        val historyStr = prefs.getString("search_history", "") ?: ""
        searchHistoryList = if (historyStr.isNotEmpty()) historyStr.split("|||") else emptyList()
    }
    
    val addSearchHistory = { searchQuery: String ->
        val current = searchHistoryList.toMutableList()
        current.remove(searchQuery)
        current.add(0, searchQuery)
        val maxHistory = current.take(10)
        searchHistoryList = maxHistory
        val prefs = context.getSharedPreferences("search_cache_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("search_history", maxHistory.joinToString("|||")).apply()
    }

    val searchSongs: (String) -> Unit = { searchQuery ->
        if (searchQuery.isNotBlank()) {
            focusManager.clearFocus()
            isLoading = true
            addSearchHistory(searchQuery)
            scope.launch(Dispatchers.IO) {
                val searchResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG)
                withContext(Dispatchers.Main) {
                    searchResult.onSuccess { result ->
                        results = result.items.filterIsInstance<SongItem>()
                    }.onFailure {
                        results = emptyList()
                    }
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            results = emptyList() // clear results to show suggestions
            // delay to debounce typing
            delay(300)
            scope.launch(Dispatchers.IO) {
                val suggestionResult = YouTube.searchSuggestions(query)
                withContext(Dispatchers.Main) {
                    suggestionResult.onSuccess { result ->
                        suggestions = result.queries
                    }.onFailure {
                        suggestions = emptyList()
                    }
                }
            }
        } else {
            suggestions = emptyList()
            results = emptyList()
        }
    }
    
    LaunchedEffect(Unit) { loadSearchHistory() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (currentSong != null) 92.dp else 0.dp)
        ) {
            // New Header Aesthetics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layerBackdrop(backdrop)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Polished Search Input
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(percent = 50)
                            ),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchSongs(query) }),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        Text("What do you want to listen to?", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 16.sp)
                                    }
                                    innerTextField()
                                }
                                if (query.isNotEmpty()) {
                                    IconButton(
                                        onClick = { query = "" },
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CupertinoActivityIndicator(modifier = Modifier.padding(16.dp))
                }
            } else if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Top Results",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(results) { song ->
                        SearchSongRow(
                            song = song,
                            onClick = {
                                val songList = results.filterIsInstance<SongItem>()
                                val idx = songList.indexOf(song).takeIf { it >= 0 } ?: 0
                                PlayerManager.playQueue(songList, idx)
                            },
                            onAddClick = { showAddToPlaylistForSong = song }
                        )
                    }
                }
            } else if (query.isNotEmpty() && suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    query = suggestion
                                    searchSongs(suggestion)
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = suggestion, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (searchHistoryList.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                TextButton(
                                    onClick = {
                                        searchHistoryList = emptyList()
                                        context.getSharedPreferences("search_cache_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit().remove("search_history").apply()
                                    }
                                ) {
                                    Text(
                                        text = "Clear",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(searchHistoryList) { historyQuery ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable {
                                        query = historyQuery
                                        searchSongs(historyQuery)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(text = historyQuery, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.ArrowOutward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(text = "Find your next favorite track", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
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
    
    showAddToPlaylistForSong?.let { song ->
        com.arcadesoftware.musix.components.AddToPlaylistSheet(song = song, onDismiss = { showAddToPlaylistForSong = null })
    }
}

@Composable
fun SearchSongRow(song: SongItem, onClick: () -> Unit, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            song.duration?.let { dur ->
                val mins = dur / 60
                val secs = dur % 60
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%d:%02d", mins, secs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
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

        IconButton(onClick = onAddClick) {
            Icon(Icons.Rounded.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}
