package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.example.data.db.AudiobookEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.DigiBookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    viewModel: DigiBookViewModel,
    modifier: Modifier = Modifier
) {
    val books by viewModel.audiobooks.collectAsState()
    val book = remember(books, bookId) { books.find { it.id == bookId } }

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val isPlayerLoading by viewModel.isPlayerLoading.collectAsState()
    val sleepTimerMin by viewModel.sleepTimerMinutes.collectAsState()

    if (book == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(DeepDarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("No active book selected", color = TextCreamWhite)
        }
        return
    }

    // Animation rotation for spinning vinyl record
    val infiniteTransition = rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Formatted positions (HH:MM:SS)
    fun formatTime(milliseconds: Long): String {
        val totalSecs = milliseconds / 1000
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("player_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    // Quick sleep timer display
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .background(
                                color = if (sleepTimerMin != null) AmberTertiary.copy(alpha = 0.15f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh, // Placeholder for Clock
                                contentDescription = null,
                                tint = if (sleepTimerMin != null) AmberTertiary else TextMutedGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (sleepTimerMin != null) "${sleepTimerMin}m remaining" else "Timer Off",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (sleepTimerMin != null) AmberTertiary else TextMutedGray
                            )
                        }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Artwork section with circular record spinning effect when playing!
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(SurfaceDarkCard)
                        .rotate(if (isPlaying) rotationAngle else 0f)
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

                // Vinyl outer border design
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .rotate(0f)
                )
            }

            // Titles Set
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextCreamWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Narrator: ${book.narrator}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGray,
                    modifier = Modifier.padding(top = 2.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Seek Controller & Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentPositionRatio = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
                
                // Live time display numbers
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    Text(text = formatTime(currentPos), color = TextCreamWhite, style = MaterialTheme.typography.bodySmall)
                    Text(text = "-" + formatTime((duration - currentPos).coerceAtLeast(0L)), color = TextMutedGray, style = MaterialTheme.typography.bodySmall)
                }

                // Smooth luxury slider scrubber
                Slider(
                    value = currentPositionRatio.coerceIn(0f, 1f),
                    onValueChange = { ratio ->
                        val targetMs = (ratio * duration).toLong()
                        viewModel.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary,
                        inactiveTrackColor = SurfaceLighterCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_scrubber")
                )
            }

            // Primary control buttons (Rewind 10, Play/Pause, Forward 30)
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                // Rewind 10
                IconButton(
                    onClick = { viewModel.skipBackward() },
                    modifier = Modifier
                        .size(54.dp)
                        .background(SurfaceDarkCard, CircleShape)
                        .testTag("skip_backward_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Skip back 10s",
                        tint = GoldPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(180f) // Rotated playArrow represents skip back beautifully!
                    )
                }

                // Huge Pulsing Play/Pause button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isPlayerLoading) { viewModel.togglePlayPause() }
                        .background(GoldPrimary)
                        .testTag("play_pause_hero_btn")
                ) {
                    if (isPlayerLoading) {
                        CircularProgressIndicator(
                            color = DeepDarkBackground,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = DeepDarkBackground,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                // Fast Forward 30
                IconButton(
                    onClick = { viewModel.skipForward() },
                    modifier = Modifier
                        .size(54.dp)
                        .background(SurfaceDarkCard, CircleShape)
                        .testTag("skip_forward_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Skip forward 30s",
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Playback rate selector row
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = "PLAYBACK SPEED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = TextMutedGray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { option ->
                        val isCurrentSpeed = speed == option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentSpeed) GoldPrimary else SurfaceDarkCard,
                            border = BorderStroke(1.dp, if (isCurrentSpeed) GoldPrimary else SurfaceLighterCard),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPlaybackSpeed(option) }
                                .testTag("speed_option_$option")
                        ) {
                            Text(
                                text = "${option}x",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isCurrentSpeed) DeepDarkBackground else TextMutedGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Sleep Timer control row
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Text(
                    text = "SLEEP TIMER",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = TextMutedGray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 30, 60).forEach { min ->
                        val isCurrentTimer = sleepTimerMin != null && sleepTimerMin == min
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentTimer) AmberTertiary else SurfaceDarkCard,
                            border = BorderStroke(1.dp, if (isCurrentTimer) AmberTertiary else SurfaceLighterCard),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.toggleSleepTimer(min) }
                                .testTag("sleep_timer_$min")
                        ) {
                            Text(
                                text = "${min}m",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isCurrentTimer) DeepDarkBackground else TextMutedGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Sync connection health on footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (book.serverSyncStatus == AudiobookEntity.SYNC_STATUS_OK) TextGreenSync else AmberTertiary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (book.serverSyncStatus == AudiobookEntity.SYNC_STATUS_OK) "Playback state synced securely" else "Offline playback (will sync when online)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}
