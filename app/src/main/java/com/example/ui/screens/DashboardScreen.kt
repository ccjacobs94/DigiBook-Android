package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.AudiobookEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.DigiBookViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DigiBookViewModel,
    modifier: Modifier = Modifier
) {
    val books by viewModel.audiobooks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentBook by viewModel.currentPlayingBook.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Downloaded", "In Progress"

    // Filter books based on criteria
    val filteredBooks = remember(books, searchQuery, selectedFilter) {
        books.filter { book ->
            val matchesSearch = book.title.contains(searchQuery, ignoreCase = true) ||
                    book.author.contains(searchQuery, ignoreCase = true)
            
            val matchesFilter = when (selectedFilter) {
                "Downloaded" -> book.isDownloaded
                "In Progress" -> book.currentPosition > 0 && !book.isCompleted
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "My Library",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextCreamWhite
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRefreshing) AmberTertiary else TextGreenSync)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRefreshing) "Syncing with Server..." else "Online & Synced",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRefreshing) AmberTertiary else TextGreenSync
                            )
                        }
                    }
                },
                actions = {
                    // Refresh Button
                    IconButton(
                        onClick = { viewModel.refreshLibrary() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync library",
                            tint = GoldPrimary
                        )
                    }
                    // Logout / Connection Settings
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Disconnect server",
                            tint = TextRedPending
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = DeepDarkBackground,
                    titleContentColor = TextCreamWhite
                )
            )
        },
        bottomBar = {
            // Elegant Mini-Player popup bar if anything is playing
            AnimatedVisibility(
                visible = currentBook != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                currentBook?.let { activeBook ->
                    MiniPlayerBar(
                        book = activeBook,
                        isPlaying = isPlaying,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onBarClick = { viewModel.navigateTo(Screen.Player(activeBook.id)) }
                    )
                }
            }
        },
        containerColor = DeepDarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title, authors...", color = TextMutedGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMutedGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SurfaceLighterCard,
                    unfocusedBorderColor = SurfaceDarkCard,
                    focusedTextColor = TextCreamWhite,
                    unfocusedTextColor = TextCreamWhite,
                    focusedContainerColor = SurfaceDarkCard,
                    unfocusedContainerColor = SurfaceDarkCard
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag("search_field"),
                shape = RoundedCornerShape(14.dp)
            )

            // Filtering Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("All", "Downloaded", "In Progress").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = DeepDarkBackground,
                            containerColor = SurfaceDarkCard,
                            labelColor = TextMutedGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) GoldPrimary else SurfaceLighterCard,
                            enabled = true,
                            selected = isSelected
                        ),
                        modifier = Modifier.testTag("filter_$filter")
                    )
                }
            }

            // Grid / List View
            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextMutedGray.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No results matching yours" else "Offline library is empty",
                            color = TextMutedGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect online or refresh using client buttons.",
                            color = TextMutedGray.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        AudiobookGridItem(
                            book = book,
                            onClick = { viewModel.navigateTo(Screen.BookDetail(book.id)) }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------ Miniature Audiobook Card ------------------------

@Composable
fun AudiobookGridItem(
    book: AudiobookEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SurfaceLighterCard.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("book_card_${book.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(SurfaceLighterCard)
            ) {
                // High fidelity Coil cover image loading
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover for ${book.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Optional downloaded/cached indicator flag
                if (book.isDownloaded) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .background(DeepDarkBackground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded for offline use",
                            tint = TextGreenSync,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Download active progress indicator overlay
                if (!book.isDownloaded && book.downloadProgress > 0.05f && book.downloadProgress < 0.99f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = book.downloadProgress,
                            color = GoldPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextCreamWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Render dynamic progress pill under book if it was partially read
                if (book.currentPosition > 0) {
                    val progressRatio = book.currentPosition.toFloat() / book.duration.toFloat().coerceAtLeast(1f)
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = progressRatio.coerceIn(0f, 1f),
                            color = GoldPrimary,
                            trackColor = SurfaceLighterCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        ) {
                            Text(
                                text = "${(progressRatio * 100).toInt()}% read",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedGray,
                                fontSize = 9.sp
                            )
                            if (book.serverSyncStatus == AudiobookEntity.SYNC_STATUS_PENDING) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Pending Sync",
                                    tint = AmberTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------ Miniature Bottom Pop Player ------------------------

@Composable
fun MiniPlayerBar(
    book: AudiobookEntity,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceDarkCard,
        tonalElevation = 12.dp,
        border = BorderStroke(1.dp, SurfaceLighterCard),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBarClick)
            .testTag("mini_player_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding() // Support Edge-to-Edge!
        ) {
            // Rounded mini picture
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(book.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceLighterCard)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextCreamWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.testTag("mini_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = GoldPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
