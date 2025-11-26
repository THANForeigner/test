package com.example.afinal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage // Import this
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // Import this for await()

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) return

        if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            triggeringGeofences?.forEach { geofence ->
                val locationId = geofence.requestId

                // Keep receiver alive for async work
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        fetchAndNotify(context, locationId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun fetchAndNotify(context: Context, locationId: String) {
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance() // Initialize Storage

        // 1. Check if it is an INDOOR location
        val indoorRef = db.collection("locations").document("locations")
            .collection("indoor_locations").document(locationId)

        val isIndoor = try {
            val snapshot = indoorRef.get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }

        // 2. Define the Query
        val postsQuery = if (isIndoor) {
            indoorRef.collection("floor").document("1").collection("posts")
        } else {
            db.collectionGroup("posts")
        }

        try {
            val querySnapshot = postsQuery.get().await()

            // 3. Find the document
            val storyDoc = if (isIndoor) {
                querySnapshot.documents.firstOrNull()
            } else {
                querySnapshot.documents.firstOrNull { doc ->
                    doc.reference.path.contains(locationId)
                }
            }

            if (storyDoc != null) {
                val title = storyDoc.getString("title") ?: "New Story Available"
                val rawAudioUrl = storyDoc.getString("audioURL") ?: ""
                val user = storyDoc.getString("user") ?: "Student Stories"

                if (rawAudioUrl.isNotEmpty()) {
                    // --- FIX STARTS HERE ---
                    // Convert gs:// to https:// if necessary
                    val playableUrl = if (rawAudioUrl.startsWith("gs://")) {
                        try {
                            // Fetch the download URL from Firebase Storage
                            storage.getReferenceFromUrl(rawAudioUrl).downloadUrl.await().toString()
                        } catch (e: Exception) {
                            Log.e("GeofenceReceiver", "Error resolving storage URL", e)
                            null
                        }
                    } else {
                        rawAudioUrl
                    }

                    // Only send notification if we have a valid playable URL
                    if (playableUrl != null) {
                        sendNotificationWithPlay(context, locationId, title, playableUrl, user)
                    }
                    // --- FIX ENDS HERE ---
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            putExtra("notification_story_id", locationId)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            locationId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Intent 2: Play Audio Service
        val playIntent = Intent(context, AudioPlayerService::class.java).apply {
            action = AudioPlayerService.ACTION_PLAY
            putExtra(AudioPlayerService.EXTRA_AUDIO_URL, audioUrl) // This is now a valid HTTP URL
            putExtra(AudioPlayerService.EXTRA_TITLE, title)
            putExtra(AudioPlayerService.EXTRA_USER, user)
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