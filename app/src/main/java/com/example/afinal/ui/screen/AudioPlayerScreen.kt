package com.example.afinal.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.afinal.data.repository.StoryRepository
import com.example.afinal.ui.theme.FINALTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(navController: NavController, storyId: String) {
    val story = remember(storyId) {
        StoryRepository.getStoryById(storyId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(story?.locationName ?: "Detail") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Story not found.", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = story.locationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = story.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Justify
                )
                Spacer(modifier = Modifier.weight(1f)) // Đẩy trình phát nhạc xuống dưới

                // Trình phát nhạc (Giả lập)
                AudioPlayerControls()
            }
        }
    }
}

@Composable
fun AudioPlayerControls() {
    // Đây là các trạng thái GIẢ LẬP.
    // Sau này bạn sẽ thay bằng trạng thái của MediaPlayer/ExoPlayer
    var isPlaying by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0.3f) } // Giả lập đang ở 30%
    val totalDuration = "05:00" // Giả lập
    val currentTime = "01:30" // Giả lập

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(currentTime)
            Text(totalDuration)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: Tua lại */ }) {
                Icon(Icons.Default.FastRewind, contentDescription = "Tua lại", modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = { /* TODO: Tua đi */ }) {
                Icon(Icons.Default.FastForward, contentDescription = "Tua đi", modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAudioPlayerScreen() {
    FINALTheme() {
        // Lấy story đầu tiên từ MockData để preview
        val previewStory = StoryRepository.getStoryById("dinhdoclap")
        AudioPlayerScreen(
            navController = rememberNavController(),
            storyId = previewStory?.id ?: "dinhdoclap"
        )
    }
}