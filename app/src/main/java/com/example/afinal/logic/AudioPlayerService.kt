package com.example.afinal.logic

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.afinal.R
import com.example.afinal.models.LocationModel
import com.example.afinal.ultis.DistanceCalculator
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

class AudioPlayerService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
        private set
    private lateinit var mediaSession: MediaSessionCompat

    var currentAudioUrl: String? = null
        private set
    var currentStoryId: String? = null
        private set

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val allLocations = mutableListOf<LocationModel>()

    // Notification State
    private var currentTitle: String = "Waiting for location..."
    private var currentUser: String = "Student Stories"
    private var currentLocationName: String = ""
    private var lastNotifiedId: String? = null // To avoid spamming notifications

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"

        const val EXTRA_AUDIO_URL = "EXTRA_AUDIO_URL"
        const val EXTRA_STORY_ID = "EXTRA_STORY_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_USER = "EXTRA_USER"
        const val EXTRA_LOCATION = "EXTRA_LOCATION"

        const val CHANNEL_ID = "audio_playback_channel"
        const val DISCOVERY_CHANNEL_ID = "discovery_channel"
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioPlayerService")
        mediaSession.isActive = true

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation ?: return

                // 1. Find the nearest location using DistanceCalculator
                val nearest = DistanceCalculator.findNearestLocation(
                    location.latitude,
                    location.longitude,
                    allLocations
                )

                // 2. If a location is found and we haven't just notified about it
                if (nearest != null && nearest.id != lastNotifiedId) {
                    Log.d("AudioService", "Discovered location: ${nearest.locationName}")
                    lastNotifiedId = nearest.id
                    fetchAndNotify(nearest)
                }
            }
        }
        fetchLocationsFromFirestore()
    }

    private fun fetchAndNotify(locationModel: LocationModel) {
        val db = FirebaseFirestore.getInstance()
        val docRef = if (locationModel.type == "indoor") {
            db.collection("locations").document("locations")
                .collection("indoor_locations").document(locationModel.id)
        } else {
            db.collection("locations").document("locations")
                .collection("outdoor_locations").document(locationModel.id)
        }

        // Assuming logic for posts based on type (indoor often has floors)
        val postsQuery = if (locationModel.type == "indoor") {
            docRef.collection("floor").document("1").collection("posts")
        } else {
            docRef.collection("posts")
        }

        postsQuery.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                val storyDoc = snapshot.documents.first()
                val title = storyDoc.getString("title") ?: "New Story"
                val user = storyDoc.getString("user") ?: "Unknown User"
                val audioUrl = storyDoc.getString("audioURL") ?: ""

                if (audioUrl.isNotEmpty()) {
                    sendDiscoveryNotification(locationModel.id, title, user, audioUrl)
                }
            }
        }.addOnFailureListener { e ->
            Log.e("AudioService", "Error fetching story details", e)
        }
    }

    private fun sendDiscoveryNotification(storyId: String, title: String, user: String, audioUrl: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager, DISCOVERY_CHANNEL_ID, "Story Discoveries")

        // Intent to open App
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_story_id", storyId)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, storyId.hashCode(), openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent to Play immediately
        val playIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_AUDIO_URL, audioUrl)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_USER, user)
            putExtra(EXTRA_STORY_ID, storyId)
        }
        val pendingPlay = PendingIntent.getService(
            this, storyId.hashCode(), playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, DISCOVERY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Nearby: $title")
            .setContentText("By $user")
            .setContentIntent(pendingOpenApp)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Now", pendingPlay)
            .build()

        manager.notify(storyId.hashCode(), notification)
    }

    // ... (UI Update methods - updateMetadata, seekTo, etc. remain the same) ...

    fun updateMetadata(title: String, user: String, location: String, id: String) {
        currentStoryId = id
        currentTitle = title
        currentUser = user
        currentLocationName = location
        showPlayerNotification(isPlaying)
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }

    fun getDuration(): Long {
        return mediaPlayer?.duration?.toLong() ?: 0L
    }

    fun playAudio(url: String) {
        if (url == currentAudioUrl && mediaPlayer != null) {
            resumeAudio()
            return
        }

        currentAudioUrl = url
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener {
                start()
                this@AudioPlayerService.isPlaying = true
                showPlayerNotification(true)
            }
            setOnCompletionListener {
                this@AudioPlayerService.isPlaying = false
                showPlayerNotification(false)
            }
        }
        startLocationUpdates()
    }

    fun resumeAudio() {
        mediaPlayer?.start()
        isPlaying = true
        showPlayerNotification(true)
        startLocationUpdates()
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        showPlayerNotification(false)
    }

    private fun fetchLocationsFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val rootRef = db.collection("locations").document("locations")
        val collections = mapOf("indoor_locations" to "indoor", "outdoor_locations" to "outdoor")

        collections.forEach { (collection, type) ->
            rootRef.collection(collection).get().addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val lat = doc.getDouble("latitude")
                    val lng = doc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        allLocations.add(
                            LocationModel(
                                id = doc.id,
                                locationName = doc.id,
                                latitude = lat,
                                longitude = lng,
                                type = type
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        intent?.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
        intent?.getStringExtra(EXTRA_USER)?.let { currentUser = it }
        intent?.getStringExtra(EXTRA_LOCATION)?.let { currentLocationName = it }
        intent?. getStringExtra(EXTRA_STORY_ID)?.let{ currentStoryId = it }
        when (action) {
            ACTION_START_TRACKING -> {
                startLocationUpdates()
                showPlayerNotification(isPlaying)
            }
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_AUDIO_URL)
                if (url != null) playAudio(url) else resumeAudio()
            }
            ACTION_PAUSE -> {
                pauseAudio()
            }
        }
        // Important: If we want the service to keep running to check location,
        // we must return START_STICKY and ensure startForeground is called.
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Request updates more frequently if needed, e.g., every 5 seconds
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(3f)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("AudioService", "Permission missing", e)
        }
    }

    private fun showPlayerNotification(isPlaying: Boolean) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager, CHANNEL_ID, "Background Audio")

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_story_id", currentStoryId)
        }

        val pendingContentIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val pendingIntent = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val content = if (currentLocationName.isNotEmpty()) "$currentUser • $currentLocationName" else currentUser

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(currentTitle)
            .setContentText(content)
            .setContentIntent(pendingContentIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0)
                .setMediaSession(mediaSession.sessionToken))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pendingIntent
            )
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun createChannel(manager: NotificationManager, id: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession.release()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}