package com.example.afinal.data.repository

import com.example.afinal.data.model.Story

object MockData {
    private val stories = listOf(
        Story(
            id = "dinhdoclap",
            title = "Dinh Độc Lập: Tiếng vọng lịch sử",
            description = "Một câu chuyện audio về những sự kiện đã diễn ra tại đây...",
            audioUrl = "http://example.com/audio/dinhdoclap.mp3",
            locationName = "Dinh Độc Lập",
            latitude = 10.7769,
            longitude = 106.7009
        ),
        Story(
            id = "nhathoducba",
            title = "Nhà thờ Đức Bà: Dấu ấn vượt thời gian",
            description = "Lắng nghe lịch sử của một trong những biểu tượng Sài Gòn.",
            audioUrl = "http://example.com/audio/nhathoducba.mp3",
            locationName = "Nhà thờ Đức Bà Sài Gòn",
            latitude = 10.7797,
            longitude = 106.6994
        ),
        Story(
            id = "buudienthanhpho",
            title = "Bưu điện Thành phố: Nét duyên Đông Dương",
            description = "Khám phá kiến trúc độc đáo của bưu điện lịch sử này.",
            audioUrl = "http://example.com/audio/buudien.mp3",
            locationName = "Bưu điện Trung tâm Sài Gòn",
            latitude = 10.7801,
            longitude = 106.6999
        ),
        Story(
            id = "chobenthanh",
            title = "Chợ Bến Thành: Nhịp đập thành phố",
            description = "Trải nghiệm âm thanh và cuộc sống sôi động của ngôi chợ hơn 100 tuổi.",
            audioUrl = "http://example.com/audio/chobenthanh.mp3",
            locationName = "Chợ Bến Thành",
            latitude = 10.7725,
            longitude = 106.6980
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