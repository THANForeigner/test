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
import com.example.afinal.logic.AudioPlayerService
import com.example.afinal.logic.MainActivity
import com.example.afinal.R
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // Import this for await()

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent?.hasError() == true) {
            Log.e("GeofenceReceiver", "Geofence Error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent?.geofenceTransition

        // Check for ENTER transition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            if (!triggeringGeofences.isNullOrEmpty()) {
                // 1. Call goAsync() EXACTLY ONCE here, outside the loop
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 2. Loop through all fences inside the single coroutine
                        triggeringGeofences.forEach { geofence ->
                            val locationId = geofence.requestId
                            try {
                                fetchAndNotify(context, locationId)
                            } catch (e: Exception) {
                                Log.e("GeofenceReceiver", "Error handling $locationId", e)
                            }
                        }
                    } finally {
                        // 3. Finish EXACTLY ONCE here
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun fetchAndNotify(context: Context, locationId: String) {

        try { // <--- MOVE TRY BLOCK UP HERE
            val db = FirebaseFirestore.getInstance()
            var docRef = db.collection("locations").document("locations").collection("indoor_locations")
                .document(locationId)

            // Now this is safe
            var snapshot = docRef.get().await()

            var postsQuery: Query? = null
            var isIndoor: Boolean = false
            if (snapshot.exists()) {
                postsQuery = docRef.collection("floor").document("1").collection("posts")
                isIndoor = true
            } else {
                docRef = db.collection("locations").document("locations").collection("outdoor_locations")
                    .document(locationId)
                postsQuery = docRef.collection("posts")
            }

            val querySnapshot = postsQuery.get().await()
            val storyDoc = if (isIndoor) {
                querySnapshot.documents.firstOrNull()
            } else {
                querySnapshot.documents.firstOrNull { doc ->
                    doc.reference.path.contains(locationId)
                }
            }
            if (storyDoc != null) {
                val title = storyDoc.getString("title") ?: "New Story Available"
                val user = storyDoc.getString("user") ?: "Unknown User"
                val audioUrl = storyDoc.getString("audioURL") ?: ""
                if (audioUrl.isNotEmpty()) sendNotificationWithPlay(
                    context,
                    locationId,
                    title,
                    audioUrl,
                    user
                )
            }
        } catch (e: Exception) {
            Log.e("GeofenceReceiver", "Error fetching story", e)
            e.printStackTrace()
        }
    }

    private fun sendNotificationWithPlay(context: Context, locationId: String, title: String, audioUrl: String, user: String) {
//        if (MainActivity.isAppInForeground) {
//            return
//        }
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
            action = AudioPlayerService.Companion.ACTION_PLAY
            putExtra(AudioPlayerService.Companion.EXTRA_AUDIO_URL, audioUrl) // This is now a valid HTTP URL
            putExtra(AudioPlayerService.Companion.EXTRA_TITLE, title)
            putExtra(AudioPlayerService.Companion.EXTRA_USER, user)
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