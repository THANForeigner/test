package com.example.afinal.ui.screen

import android.media.MediaPlayer
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.afinal.StoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(navController: NavController, storyId: String) {
    val storyViewModel: StoryViewModel = viewModel()
    // Get the specific story from the loaded list
    val story = storyViewModel.currentStories.value.find { it.id == storyId }
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    val mediaPlayer = remember { MediaPlayer() }

    // Load the audio when story is ready
    LaunchedEffect(story) {
        if (story != null && story.playableUrl.isNotEmpty()) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(story.playableUrl)
                mediaPlayer.prepareAsync() // Async to avoid UI freeze
                mediaPlayer.setOnPreparedListener { mp ->
                    totalDuration = mp.duration.toLong()
                }
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                    it.seekTo(0)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    // Update slider
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer.currentPosition.toLong()
            kotlinx.coroutines.delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(story?.title ?: "Playing Story") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (story == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Story not found or loading...")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(story.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(story.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.weight(1f))

                // Slider
                Slider(
                    value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                    onValueChange = { newPercent ->
                        val newPosition = (newPercent * totalDuration).toLong()
                        currentPosition = newPosition
                        mediaPlayer.seekTo(newPosition.toInt())
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
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
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

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}