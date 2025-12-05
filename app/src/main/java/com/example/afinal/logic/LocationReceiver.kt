package com.example.afinal.ultis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.afinal.R
import com.example.afinal.logic.AudioPlayerService
import com.example.afinal.logic.MainActivity
import com.example.afinal.models.LocationModel
import com.google.android.gms.location.LocationResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationReceiver : BroadcastReceiver() {

    companion object {
        const val DISCOVERY_CHANNEL_ID = "discovery_channel"
        private const val TAG = "LocationReceiverDebug"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent)
            val location = result?.lastLocation ?: return

            Log.d(TAG, ">>> Background Location: ${location.latitude}, ${location.longitude}")

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    checkStoriesNearby(context, location.latitude, location.longitude)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking stories", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun checkStoriesNearby(context: Context, lat: Double, lng: Double) {
        val db = FirebaseFirestore.getInstance()
        val allLocations = mutableListOf<LocationModel>()

        val collections = mapOf("indoor_locations" to "indoor", "outdoor_locations" to "outdoor")

        for ((collectionName, type) in collections) {
            val snapshot = db.collection("locations").document("locations")
                .collection(collectionName).get().await()

            for (doc in snapshot.documents) {
                val lLat = doc.getDouble("latitude")
                val lLng = doc.getDouble("longitude")

                // [FETCH NEW FIELDS FOR ZONES]
                val isZone = doc.getBoolean("zone") ?: false

                val lat1 = doc.getDouble("latitude1"); val lng1 = doc.getDouble("longitude1")
                val lat2 = doc.getDouble("latitude2"); val lng2 = doc.getDouble("longitude2")
                val lat3 = doc.getDouble("latitude3"); val lng3 = doc.getDouble("longitude3")
                val lat4 = doc.getDouble("latitude4"); val lng4 = doc.getDouble("longitude4")

                if (lLat != null && lLng != null) {
                    allLocations.add(
                        LocationModel(
                            id = doc.id,
                            locationName = doc.id,
                            latitude = lLat,
                            longitude = lLng,
                            type = type,
                            isZone = isZone,
                            latitude1 = lat1, longitude1 = lng1,
                            latitude2 = lat2, longitude2 = lng2,
                            latitude3 = lat3, longitude3 = lng3,
                            latitude4 = lat4, longitude4 = lng4
                        )
                    )
                }
            }
        }

        // [USE CENTRALIZED DISTANCE LOGIC]
        // This now uses the shared logic (Zone Polygon check + Radius check)
        val currentLoc = DistanceCalculator.findCurrentLocation(lat, lng, allLocations)

        if (currentLoc != null) {
            Log.d(TAG, ">>> Entered Location: ${currentLoc.id}. Fetching story...")
            fetchFirstStoryAndNotify(context, currentLoc)
        } else {
            Log.d(TAG, ">>> No locations match.")
        }
    }

    private suspend fun fetchFirstStoryAndNotify(context: Context, location: LocationModel) {
        val db = FirebaseFirestore.getInstance()

        val docRef = if (location.type == "indoor") {
            db.collection("locations").document("locations")
                .collection("indoor_locations").document(location.id)
        } else {
            db.collection("locations").document("locations")
                .collection("outdoor_locations").document(location.id)
        }

        val postsQuery = if (location.type == "indoor") {
            docRef.collection("floor").document("1").collection("posts")
        } else {
            docRef.collection("posts")
        }

        val snapshot = postsQuery.limit(1).get().await()

        if (!snapshot.isEmpty) {
            val storyDoc = snapshot.documents.first()
            val storyId = storyDoc.id
            val title = storyDoc.getString("name") ?: "New Story"
            val user = storyDoc.getString("user") ?: "Unknown User"
            var audioUrl = storyDoc.getString("audioUrl") ?: ""

            if (audioUrl.startsWith("gs://")) {
                try {
                    val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(audioUrl)
                    audioUrl = storageRef.downloadUrl.await().toString()
                } catch (e: Exception) {
                    audioUrl = ""
                }
            }

            if (audioUrl.isNotEmpty()) {
                sendDiscoveryNotification(context, location.id, storyId, title, user, audioUrl)
            }
        }
    }

    private fun sendDiscoveryNotification(
        context: Context, locationId: String, storyId: String,
        title: String, user: String, audioUrl: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(DISCOVERY_CHANNEL_ID, "Story Discoveries", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_story_id", storyId)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context, locationId.hashCode(), openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playIntent = Intent(context, AudioPlayerService::class.java).apply {
            action = AudioPlayerService.ACTION_PLAY
            putExtra(AudioPlayerService.EXTRA_AUDIO_URL, audioUrl)
            putExtra(AudioPlayerService.EXTRA_TITLE, title)
            putExtra(AudioPlayerService.EXTRA_USER, user)
            putExtra(AudioPlayerService.EXTRA_STORY_ID, storyId)
            putExtra("EXTRA_NOTIFICATION_ID", locationId)
        }
        val pendingPlay = PendingIntent.getService(
            context, locationId.hashCode(), playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DISCOVERY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Entered: $title") // Changed text slightly
            .setContentText("Tap to listen to $user's story")
            .setContentIntent(pendingOpenApp)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Now", pendingPlay)
            .build()

        manager.notify(locationId.hashCode(), notification)
    }
}