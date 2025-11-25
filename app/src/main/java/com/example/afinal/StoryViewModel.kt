package com.example.afinal

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.afinal.data.model.LocationModel
import com.example.afinal.data.model.StoryModel
import com.google.firebase.firestore.FirebaseFirestore
// REMOVED: import com.google.firebase.firestore.toObjects (causes error)
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StoryViewModel : ViewModel() {
    private val _locations = mutableStateOf<List<LocationModel>>(emptyList())
    val locations: State<List<LocationModel>> = _locations

    private val _currentStories = mutableStateOf<List<StoryModel>>(emptyList())
    val currentStories: State<List<StoryModel>> = _currentStories

    init {
        fetchLocations()
    }

    private fun fetchLocations() {
        viewModelScope.launch {
            val db = FirebaseFirestore.getInstance()
            val loadedLocations = mutableListOf<LocationModel>()

            // Fetch from both indoor and outdoor collections
            val collectionsToFetch = listOf("indoor_locations", "outdoor_locations")

            try {
                for (collectionName in collectionsToFetch) {
                    // 1. Get all documents (e.g., "Building_I", "Flag")
                    val snapshot = db.collection(collectionName).get().await()

                    for (document in snapshot.documents) {
                        // 2. Dive into 'coordinate' subcollection -> 'coordinate' document
                        val coordDoc = document.reference
                            .collection("coordinate")
                            .document("coordinate")
                            .get()
                            .await()

                        if (coordDoc.exists()) {
                            val lat = coordDoc.getDouble("latitude") ?: 0.0
                            val lng = coordDoc.getDouble("longitude") ?: 0.0

                            // Use document ID as the name (e.g., "Building_I")
                            loadedLocations.add(
                                LocationModel(
                                    id = document.id,
                                    locationName = document.id,
                                    latitude = lat,
                                    longitude = lng
                                )
                            )
                        }
                    }
                }

                _locations.value = loadedLocations
                Log.d("Firestore", "Success: Loaded ${loadedLocations.size} locations")

            } catch (e: Exception) {
                Log.e("Firestore", "Error fetching locations: ${e.message}")
            }
        }
    }

    fun fetchStoriesForLocation(locationId: String) {
        val db = FirebaseFirestore.getInstance()

        // Try searching in indoor_locations first
        db.collection("indoor_locations").document(locationId).collection("Stories")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    // Use Class.java to avoid import errors
                    _currentStories.value = snapshot.toObjects(StoryModel::class.java)
                } else {
                    // Not found? Try outdoor_locations
                    fetchStoriesFromOutdoor(locationId)
                }
            }
            .addOnFailureListener {
                fetchStoriesFromOutdoor(locationId)
            }
    }

    private fun fetchStoriesFromOutdoor(locationId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("outdoor_locations").document(locationId).collection("Stories")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    _currentStories.value = snapshot.toObjects(StoryModel::class.java)
                } else {
                    _currentStories.value = emptyList()
                }
            }
            .addOnFailureListener {
                _currentStories.value = emptyList()
            }
    }
}