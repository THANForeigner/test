package com.example.afinal.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class StoryModel(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val user: String = "",
    @PropertyName("audioURL")
    val audioUrl: String = "",
    val likes: Int = 0,
    val dislikes: Int = 0,
    var playableUrl: String = "",
    var locationName: String = ""
)