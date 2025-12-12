package com.example.afinal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.afinal.models.StoryViewModel
import com.example.afinal.ui.component.StoryCard
import com.example.afinal.ui.theme.AppGradients

@Composable
fun HomeScreen(
    storyViewModel: StoryViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToAudios: () -> Unit,
    onStoryClick: (String) -> Unit
) {
    val trendingStories by storyViewModel.topTrendingStories

    // Gradient Cam-Đỏ cho icon Trending (Ngọn lửa)
    val fireGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF8008), Color(0xFFFFC837)) // Cam sang vàng/đỏ
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // --- LAYER 1: GRADIENT BACKGROUND ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp) // Giảm độ cao một chút cho cân đối
                .background(brush = AppGradients.homeScreen)
        )

        // --- LAYER 2: SCROLLABLE CONTENT ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // A. Header Text & Welcome
            item {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 60.dp, bottom = 24.dp)
                ) {
                    // Header Icon nhỏ
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Student Stories",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Slogan
                    Text(
                        text = "Discover the hidden tales of your school",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // B. Custom Action Buttons (Map & Stories)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. EXPLORE MAP BUTTON
                    HomeMenuCard(
                        title = "Explore Map",
                        icon = Icons.Default.Place, // Icon Check-in/Map
                        gradient = AppGradients.mapScreen,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToMap
                    )

                    // 2. BROWSE STORIES BUTTON
                    HomeMenuCard(
                        title = "Browse Stories",
                        icon = Icons.Default.Headphones,
                        gradient = AppGradients.audioScreen, // Icon Tai nghe
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAudios
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // C. White Sheet Container (Trending Section)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(Color.White)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Title Section: Trending
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Icon Ngọn lửa Cam Đỏ
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(brush = fireGradient), // Dùng gradient lửa
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Trending Stories",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF2D2D2D)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Loading State
                        if (trendingStories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            // D. List Items (Nằm ngoài Box container ở trên để background trắng liền mạch)
            items(trendingStories) { story ->
                // Wrapper Box để giữ background trắng
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 24.dp)
                ) {
                    StoryCard(
                        story = story,
                        onClick = {
                            onStoryClick(story.id)
                        }
                    )
                }
            }

            // E. Footer Filler
            item {
                Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.White))
            }
        }
    }
}

// Composable riêng cho nút bấm menu (Inline button style)
@Composable
fun HomeMenuCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = Color.LightGray.copy(alpha = 0.5f))
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp), // Bo góc lớn như mẫu
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Tắt elevation mặc định để dùng shadow custom đẹp hơn
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Container (Gradient Background)
            Box(
                modifier = Modifier
                    .size(56.dp) // Kích thước vòng tròn màu
                    .clip(RoundedCornerShape(18.dp)) // Bo cong mềm (Squircle) giống mẫu
                    .background(brush = gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text Title
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D2D2D)
            )
        }
    }
}