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
    private val _allStories = mutableStateOf<List<StoryModel>>(emptyList())
    val allStories: State<List<StoryModel>> = _allStories

    init {
        fetchLocations()
        fetchAllStories()
    }

    fun getStory(id: String): StoryModel? {
        return _allStories.value.find { it.id == id } ?: _allStories.value.find { it.id == id }
    }
    private fun fetchLocations() {
        viewModelScope.launch {
            val db = FirebaseFirestore.getInstance()
            val loadedLocations = mutableListOf<LocationModel>()
            val rootRef = db.collection("locations").document("locations")
            val collectionsToFetch = listOf("indoor_locations", "outdoor_locations")

            try {
                for (collectionName in collectionsToFetch) {
                    val snapshot = rootRef.collection(collectionName).get().await()
                    for (document in snapshot.documents) {
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

    private fun fetchAllStories(){
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()
        db.collectionGroup("posts").get()
            .addOnSuccessListener{snapshot ->
                val storiesLists = mutableListOf<StoryModel>()
                if(snapshot.isEmpty) return@addOnSuccessListener
                var processedCount = 0
                for(doc in snapshot.documents){
                    val story = doc.toObject(StoryModel::class.java)?.copy(id = doc.id)
                    val pathSegments = doc.reference.path.split("/")
                    if(pathSegments.size>3){
                        story?.locationName = pathSegments[3]
                    }
                    if(story!=null && story.audioUrl.startsWith("gs://")) {
                        storage.getReferenceFromUrl(story.audioUrl).downloadUrl.addOnSuccessListener { uri ->
                            story.playableUrl = uri.toString()
                            processedCount++
                            if (processedCount == snapshot.size()) _allStories.value = storiesLists
                        }.addOnFailureListener {
                            Log.e("Storage", "Failed to resolve URL for ${story.title}")
                            processedCount++
                            if (processedCount == snapshot.size()) _allStories.value = storiesLists
                        }
                    } else if (story != null) {
                        story.playableUrl = story.audioUrl
                        storiesLists.add(story)
                        processedCount++
                        if (processedCount == snapshot.size()) _allStories.value = storiesLists
                    }
                    }
            }
    }

    fun fetchStoriesForLocation(locationId: String) {
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        db.collectionGroup("posts").get()
            .addOnSuccessListener { snapshot ->
                val storiesList = mutableListOf<StoryModel>()
                val matches = snapshot.documents.filter { it.reference.path.contains(locationId) }

                if (matches.isEmpty()) {
                    _currentStories.value = emptyList()
                    return@addOnSuccessListener
                }

                var processedCount = 0
                for (doc in matches) {
                    val story = doc.toObject(StoryModel::class.java)?.copy(id = doc.id)
                    story?.locationName = locationId // We know the location for these

                    if (story != null && story.audioUrl.startsWith("gs://")) {
                        storage.getReferenceFromUrl(story.audioUrl).downloadUrl
                            .addOnSuccessListener { uri ->
                                story.playableUrl = uri.toString()
                                storiesList.add(story)
                                processedCount++
                                if (processedCount == matches.size) _currentStories.value = storiesList
                            }
                            .addOnFailureListener {
                                processedCount++
                                if (processedCount == matches.size) _currentStories.value = storiesList
                            }
                    } else if (story != null) {
                        story.playableUrl = story.audioUrl
                        storiesList.add(story)
                        processedCount++
                        if (processedCount == matches.size) _currentStories.value = storiesList
                    }
                }
            }
    }
}