package com.example.afinal.data.repository

import com.example.afinal.data.model.Story
import com.example.afinal.R

object MockData {
    private val stories = listOf(
        Story(
            id = "hcmus",
            title = "Trường Đại học Khoa học tự nhiên: Hình thành và phát triển",
            description = "Description...",
            audioUrl = "http://example.com/audio/hcmus.mp3",
            locationName = "Trường Đại học Khoa học tự nhiên",
            latitude = 10.763046391320858,
            longitude = 106.68245020890055,
            audioResourceId = R.raw.hcmus
        ),
        Story(
            id = "fitus",
            title = "Khoa Công nghệ thông tin",
            description = "Description...",
            audioUrl = "http://example.com/audio/fitus.mp3",
            locationName = "Văn phòng khoa Công nghệ thông tin",
            latitude = 10.762589158993256,
            longitude = 106.68244896076847,
            audioResourceId = R.raw.hcmus
        ),
        Story(
            id = "thuvien",
            title = "Thư viện tầng 10",
            description = "description...",
            audioUrl = "http://example.com/audio/thuvien.mp3",
            locationName = "Thư viện tầng 10",
            latitude = 10.762592134278984,
            longitude = 106.68237592089505,
            audioResourceId = R.raw.hcmus
        ),
        Story(
            id = "trungtamtinhoc",
            title = "Trung tâm tin học Đại học Khoa học tự nhiên",
            description = "Description...",
            audioUrl = "http://example.com/audio/trungtamtinhoc.mp3",
            locationName = "Trung tâm tin học",
            latitude = 10.762920466971467,
            longitude = 106.68223594801846,
            audioResourceId = R.raw.hcmus
        )
    )

    fun getAllStories(): List<Story> {
        return stories
    }

    fun getStoryById(id: String): Story? {
        return stories.find { it.id == id }
    }

    fun getFeaturedStory(): Story? {
        return stories.firstOrNull()
    }

    fun getNearbyStories(): List<Story> {
        return stories.drop(1)
    }
}