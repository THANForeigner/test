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
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent!!.hasError()) {
            Log.e("GeofenceReceiver", "Lỗi Geofence: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        // Nếu sự kiện là đi VÀO vùng (ENTER)
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            triggeringGeofences?.forEach { geofence ->
                val locationId = geofence.requestId
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch{
                    try{
                        fetchAndNoify(context, locationId)
                    }finally{
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun fetchAndNoify(context: Context, locationId: String){
        val db = FirebaseFirestore.getInstance()
        var docRef = db.collection("locations").document("locations").collection("indoor_locations").document(locationId)
        var snapshot = docRef.get().await()
        var postsQuery: Query? = null
        var isIndoor: Boolean = false
        if (snapshot.exists()){
            postsQuery = docRef.collection("floor").document("1").collection("posts")
            isIndoor = true
        } else {
            docRef = db.collection("locations").document("locations").collection("outdoor_locations").document(locationId)
            postsQuery = docRef.collection("posts")
        }
        try {
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
                val audioUrl = storyDoc.getString("audioURL") ?: ""
                if(audioUrl.isNotEmpty()) sendNotificationWithPlay(context, locationId, title, audioUrl)
            }
        }catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendNotificationWithPlay(context: Context, locationId: String, title: String, audioUrl: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "story_alert_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Story Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // 1. Intent to Open App (Clicking body)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("notification_story_id", locationId)
        }
        val pendingOpenApp = PendingIntent.getActivity(context, locationId.hashCode(), openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        // 2. Intent to Play Audio directly (Clicking "Play Now")
        val playIntent = Intent(context, AudioPlayerService::class.java).apply {
            action = AudioPlayerService.ACTION_PLAY
            putExtra(AudioPlayerService.EXTRA_AUDIO_URL, audioUrl)
            putExtra(AudioPlayerService.EXTRA_TITLE, title)
        }
        // Use standard PendingIntent.getService
        val pendingPlay = PendingIntent.getService(
            context,
            locationId.hashCode(),
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Nearby: $title")
            .setContentText("Tap 'Play' to listen while you walk!")
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_media_play, "Play Now", pendingPlay) // <--- The Magic Button
            .setAutoCancel(true)
            .build()

        notificationManager.notify(locationId.hashCode(), notification)
    }
}