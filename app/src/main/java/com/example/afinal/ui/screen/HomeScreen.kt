package com.example.afinal.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.afinal.navigation.Routes
import com.example.afinal.ui.theme.FINALTheme
import com.example.afinal.data.repository.StoryRepository

@Composable
fun HomeScreen(navController: NavController) {
    val featuredStory = StoryRepository.getFeaturedStory()
    val nearbyStories = StoryRepository.getNearbyStories()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("Student Stories!", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Your location:", style = MaterialTheme.typography.titleMedium)
            val gpsText = if (featuredStory != null) {
                "${featuredStory.latitude}° N, ${featuredStory.longitude}° E (${featuredStory.locationName})"
            } else {
                "Không tìm thấy vị trí"
            }
            Text(gpsText, style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))

            Text("Available stories:", style = MaterialTheme.typography.titleMedium)

            // Logic: Nếu ở gần, hiển thị câu chuyện

            if (featuredStory != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = {
                        navController.navigate("${Routes.AUDIO_PLAYER}/${featuredStory.id}")
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(featuredStory.title, style = MaterialTheme.typography.titleMedium)
                        Text(featuredStory.description)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Nearby location:", style = MaterialTheme.typography.titleMedium)
        }

        items(nearbyStories) { story ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = {
                    navController.navigate("${Routes.AUDIO_PLAYER}/${story.id}")
                }
            ) {
                Text(story.title, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    FINALTheme() {
        HomeScreen(navController = rememberNavController())
    }
}