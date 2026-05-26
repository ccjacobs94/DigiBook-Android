package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.*
import com.example.ui.viewmodel.DigiBookViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    viewModel: DigiBookViewModel,
    modifier: Modifier = Modifier
) {
    val books by viewModel.audiobooks.collectAsState()
    val book = remember(books, bookId) { books.find { it.id == bookId } }

    if (book == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(DeepDarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("Audiobook not found", color = TextCreamWhite)
        }
        return
    }

    // Convert total duration Ms to hours and minutes
    val totalSeconds = book.duration / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val formattedDuration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepDarkBackground,
                    titleContentColor = TextCreamWhite
                )
            )
        },
        containerColor = DeepDarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant large cover frame
            Card(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, SurfaceLighterCard),
                modifier = Modifier
                    .size(220.dp)
                    .aspectRatio(1f)
                    .testTag("detail_cover")
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metadata info
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextCreamWhite,
                textAlign = TextAlign.Center
            )

            Text(
                text = "By ${book.author}",
                style = MaterialTheme.typography.titleMedium,
                color = GoldPrimary,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Narrated by ${book.narrator}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGray,
                modifier = Modifier.padding(top = 2.dp),
                textAlign = TextAlign.Center
            )

            // Duration Pill Row
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = { },
                    label = { Text("Length: $formattedDuration", color = TextCreamWhite) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                    border = SuggestionChipDefaults.suggestionChipBorder(borderColor = SurfaceLighterCard, enabled = true)
                )

                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (book.isDownloaded) "Offline Cache Active" else "Stream Ready",
                            color = if (book.isDownloaded) TextGreenSync else TextMutedGray
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (book.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (book.isDownloaded) TextGreenSync else TextMutedGray,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    border = SuggestionChipDefaults.suggestionChipBorder(borderColor = SurfaceLighterCard, enabled = true)
                )
            }

            // Description Segment
            Surface(
                color = SurfaceDarkCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SurfaceLighterCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Synopsis",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextCreamWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMutedGray,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Play CTA
            Button(
                onClick = {
                    viewModel.playAudiobook(book)
                    viewModel.navigateTo(Screen.Player(book.id))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepDarkBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("detail_play_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (book.currentPosition > 0) "RESUME AUDIOBOOK" else "START LISTENING",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Offline Caching Manager Button (Download/Remove)
            if (book.isDownloaded) {
                OutlinedButton(
                    onClick = { viewModel.removeDownloadAsset(book.id) },
                    border = BorderStroke(1.dp, TextRedPending.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextRedPending),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("detail_delete_cache_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REMOVE OFFLINE CACHE",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        )
                    }
                }
            } else {
                val isDownloading = book.downloadProgress > 0.05f && book.downloadProgress < 1.0f
                Button(
                    onClick = { viewModel.downloadAsset(book.id) },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceDarkCard,
                        contentColor = TextCreamWhite,
                        disabledContainerColor = SurfaceDarkCard,
                        disabledContentColor = TextMutedGray
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SurfaceLighterCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("detail_download_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = book.downloadProgress,
                                color = GoldPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "DOWNLOADING OFFLINE CACHE ${(book.downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CACHE FOR OFFLINE PLAYBACK",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
