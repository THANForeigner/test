package com.example.afinal

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.afinal.data.model.LocationModel
import com.example.afinal.data.model.StoryModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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

            // 1. Access the root 'locations' document
            // Path: locations (col) -> locations (doc)
            val rootRef = db.collection("locations").document("locations")
            val collectionsToFetch = listOf("indoor_locations", "outdoor_locations")

            try {
                for (collectionName in collectionsToFetch) {
                    // 2. Get the sub-collection (e.g., indoor_locations)
                    val snapshot = rootRef.collection(collectionName).get().await()

                    for (document in snapshot.documents) {
                        // 3. READ FIELDS DIRECTLY FROM THE BUILDING DOCUMENT
                        // Path: .../indoor_locations/Building_I -> fields: latitude, longitude
                        val lat = document.getDouble("latitude")
                        val lng = document.getDouble("longitude")

                        if (lat != null && lng != null) {
                            loadedLocations.add(
                                LocationModel(
                                    id = document.id,
                                    locationName = document.id,
                                    latitude = lat,
                                    longitude = lng
                                )
                            )
                        } else {
                            Log.w("Firestore", "Missing coordinates for ${document.id}")
                        }
                    }
                }
                _locations.value = loadedLocations
                Log.d("Firestore", "Loaded ${loadedLocations.size} locations")

            } catch (e: Exception) {
                Log.e("Firestore", "Error fetching locations", e)
            }
        }
    }

    // Uses Collection Group to find "posts" deep in the structure (e.g. floor/1/posts)
    //
    fun fetchStoriesForLocation(locationId: String) {
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        db.collectionGroup("posts").get()
            .addOnSuccessListener { snapshot ->
                val storiesList = mutableListOf<StoryModel>()

                // Filter: Only keep posts where the path contains the Building ID (e.g. "Building_I")
                val matches = snapshot.documents.filter { doc ->
                    doc.reference.path.contains(locationId)
                }

                if (matches.isEmpty()) {
                    _currentStories.value = emptyList()
                    return@addOnSuccessListener
                }

                var processedCount = 0
                for (doc in matches) {
                    val story = doc.toObject(StoryModel::class.java)?.copy(id = doc.id)

                    if (story != null && story.audioUrl.startsWith("gs://")) {
                        // Convert gs:// to playable HTTPS URL
                        try {
                            val gsRef = storage.getReferenceFromUrl(story.audioUrl)
                            gsRef.downloadUrl.addOnSuccessListener { uri ->
                                story.playableUrl = uri.toString()
                                storiesList.add(story)
                                processedCount++
                                if (processedCount == matches.size) _currentStories.value = storiesList
                            }.addOnFailureListener {
                                Log.e("Storage", "Failed to resolve URL for ${story.title}")
                                processedCount++
                                if (processedCount == matches.size) _currentStories.value = storiesList
                            }
                        } catch (e: Exception) {
                            Log.e("Storage", "Invalid GS URL: ${story.audioUrl}")
                            processedCount++
                        }
                    } else if (story != null) {
                        story.playableUrl = story.audioUrl
                        storiesList.add(story)
                        processedCount++
                        if (processedCount == matches.size) _currentStories.value = storiesList
                    }
                }
            }
            .addOnFailureListener {
                _currentStories.value = emptyList()
                Log.e("Firestore", "Error fetching stories: ${it.message}")
            }
    }
}