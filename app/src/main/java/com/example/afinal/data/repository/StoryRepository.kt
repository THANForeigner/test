package com.example.afinal.data.repository

import com.example.afinal.data.model.Story

object StoryRepository {
    fun getAllStories(): List<Story> {
        // HIỆN TẠI: Trả về dữ liệu giả
        return MockData.getAllStories()

        // SAU NÀY (khi dùng Firebase):
        // Bạn sẽ viết code query (truy vấn) Firebase ở đây
        // và trả về một danh sách Story.
        // Giao diện (UI) sẽ không hề biết sự thay đổi này.
    }

    fun getStoryById(id: String): Story? {
        // HIỆN TẠI:
        return MockData.getStoryById(id)

        // SAU NÀY:
        // Viết code Firebase để lấy 1 doc theo ID
    }

    fun getFeaturedStory(): Story? {
        // HIỆN TẠI:
        return MockData.getFeaturedStory()

        // SAU NÀY:
        // Viết code Firebase để lấy story nổi bật
    }

    fun getNearbyStories(): List<Story> {
        // HIỆN TẠI:
        return MockData.getNearbyStories()

        // SAU NÀY:
        // Viết code Firebase để lấy các story lân cận
    }

    // Bạn có thể thêm các hàm khác ở đây, ví dụ:
    // fun getStoryById(id: String): Story? { ... }
}