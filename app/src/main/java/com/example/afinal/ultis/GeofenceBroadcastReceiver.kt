package com.example.afinal.ultis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.afinal.R
import com.example.afinal.logic.AudioPlayerService
import com.example.afinal.logic.MainActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    // Helper data class to hold fetched info for comparison
    data class StoryCandidate(
        val locationId: String,
        val title: String,
        val user: String,
        val audioUrl: String,
        val latitude: Double,
        val longitude: Double,
        val distance: Float
    )

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent?.hasError() == true) {
            Log.e("GeofenceReceiver", "Geofence Error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent?.geofenceTransition
        val triggeringLocation = geofencingEvent?.triggeringLocation

        // Check for ENTER transition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            if (!triggeringGeofences.isNullOrEmpty() && triggeringLocation != null) {
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. Fetch ALL candidates in parallel
                        val candidates = triggeringGeofences.map { geofence ->
                            async {
                                fetchCandidate(geofence.requestId, triggeringLocation)
                            }
                        }.awaitAll().filterNotNull() // Remove failed fetches

                        // 2. Find the closest one
                        val closestCandidate = candidates.minByOrNull { it.distance }

                        // 3. Notify
                        if (closestCandidate != null) {
                            Log.d("GeofenceReceiver", "Closest story: ${closestCandidate.title} (${closestCandidate.distance}m)")
                            sendNotificationWithPlay(
                                context,
                                closestCandidate.locationId,
                                closestCandidate.title,
                                closestCandidate.audioUrl,
                                closestCandidate.user
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("GeofenceReceiver", "Error processing geofences", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    // Fetches Firestore data and calculates distance
    private suspend fun fetchCandidate(locationId: String, userLocation: Location): StoryCandidate? {
        try {
            val db = FirebaseFirestore.getInstance()
            // 1. Check Indoor
            var docRef = db.collection("locations").document("locations")
                .collection("indoor_locations").document(locationId)

            var locationSnap = docRef.get().await()
            var isIndoor = false

            // 2. If not Indoor, Check Outdoor
            if (!locationSnap.exists()) {
                docRef = db.collection("locations").document("locations")
                    .collection("outdoor_locations").document(locationId)
                locationSnap = docRef.get().await()
            } else {
                isIndoor = true
            }

            if (!locationSnap.exists()) return null

            // 3. Get Coordinates from Location Document
            val lat = locationSnap.getDouble("latitude")
            val lng = locationSnap.getDouble("longitude")

            if (lat == null || lng == null) return null

            // 4. Fetch Story Details (Title, Audio)
            val postsQuery = if (isIndoor) {
                docRef.collection("floor").document("1").collection("posts")
            } else {
                docRef.collection("posts")
            }

            val postsSnap = postsQuery.get().await()
            val storyDoc = postsSnap.documents.firstOrNull() ?: return null

            val title = storyDoc.getString("title") ?: "New Story Available"
            val user = storyDoc.getString("user") ?: "Unknown User"
            val audioUrl = storyDoc.getString("audioURL") ?: ""

            if (audioUrl.isEmpty()) return null

            // 5. Calculate Distance
            val dist = DistanceCalculator.getDistance(
                userLocation.latitude, userLocation.longitude,
                lat, lng
            )

            return StoryCandidate(locationId, title, user, audioUrl, lat, lng, dist)

        } catch (e: Exception) {
            Log.e("GeofenceReceiver", "Failed to fetch candidate $locationId", e)
            return null
        }
    }

    private fun sendNotificationWithPlay(context: Context, locationId: String, title: String, audioUrl: String, user: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "story_alert_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Story Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // Intent 1: Open App
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            // FIXED: Passing the Story ID so AppNavigation can route to it
            putExtra("notification_story_id", locationId)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            locationId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent 2: Play Audio Service
        val playIntent = Intent(context, AudioPlayerService::class.java).apply {
            action = AudioPlayerService.Companion.ACTION_PLAY
            putExtra(AudioPlayerService.Companion.EXTRA_AUDIO_URL, audioUrl)
            putExtra(AudioPlayerService.Companion.EXTRA_TITLE, title)
            putExtra(AudioPlayerService.Companion.EXTRA_USER, user)
            // FIXED: Passing the Story ID here as well for metadata consistency
            putExtra(AudioPlayerService.Companion.EXTRA_STORY_ID, locationId)
        }
        val pendingPlay = PendingIntent.getService(
            context,
            locationId.hashCode(),
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Nearby: $title")
            .setContentText("By $user")
            .setContentIntent(pendingOpenApp)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Now", pendingPlay)
            .build()

        notificationManager.notify(locationId.hashCode(), notification)
    }
}