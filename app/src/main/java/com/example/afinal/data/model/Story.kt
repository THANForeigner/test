package com.example.afinal.data.model

data class Story(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double
)