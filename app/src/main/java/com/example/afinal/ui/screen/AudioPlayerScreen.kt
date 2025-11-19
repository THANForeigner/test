package com.example.afinal.ui.screen

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.afinal.R
import com.example.afinal.data.repository.StoryRepository
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(navController: NavController, storyId: String) {
    // Lấy story từ ID
    val story = remember(storyId) { StoryRepository.getStoryById(storyId) }
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    val mediaPlayer = remember(story) {
        if (story != null) {
            MediaPlayer.create(context, story.audioResourceId)?.apply {
                setOnCompletionListener {
                    isPlaying = false
                    seekTo(0)
                }
            }
        } else {
            null
        }
    }

    LaunchedEffect(mediaPlayer) {
        mediaPlayer?.let {
            totalDuration = it.duration.toLong()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                currentPosition = it.currentPosition.toLong()
            }
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(story?.locationName ?: "Chi tiết") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (story == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không tìm thấy câu chuyện.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(story.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(story.locationName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(story.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Justify)

                Spacer(modifier = Modifier.weight(1f))

                // --- PHẦN ĐIỀU KHIỂN ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Thanh trượt
                    Slider(
                        value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                        onValueChange = { newPercent ->
                            val newPosition = (newPercent * totalDuration).toLong()
                            currentPosition = newPosition
                            mediaPlayer?.seekTo(newPosition.toInt())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition))
                        Text(formatTime(totalDuration))
                    }

                    IconButton(
                        onClick = {
                            mediaPlayer?.let { mp ->
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}