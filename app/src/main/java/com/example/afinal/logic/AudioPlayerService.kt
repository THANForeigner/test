package com.example.afinal.logic

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
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

    // --- Audio Components ---
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private lateinit var mediaSession: MediaSessionCompat

    // --- Location Components ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val allLocations = mutableListOf<LocationModel>()
    private var lastDiscoveredLocationId: String? = null

    // Track Notification State
    private var currentTitle: String = "Waiting for location..."
    private var currentUser: String = "Student Stories"
    private var currentLocationName: String = ""

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING" // New Action

        const val EXTRA_AUDIO_URL = "EXTRA_AUDIO_URL"
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

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define what happens when location changes
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation ?: return

                // 1. Calculate Distance using the separate file
                val nearest = DistanceCalculator.findNearestLocation(
                    location.latitude,
                    location.longitude,
                    allLocations
                )
            }
        }

        // Fetch locations immediately so the service knows what to look for
        fetchLocationsFromFirestore()
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
                Log.d("AudioService", "Loaded ${allLocations.size} locations for background tracking")
            }
        }
    }

    private fun fetchAndNotify(location: LocationModel) {
        // Simple fetch logic for the "Newest" story to show in notification
        val db = FirebaseFirestore.getInstance()
        val query = if (location.type == "indoor") {
            db.collection("locations").document("locations")
                .collection("indoor_locations").document(location.id)
                .collection("floor").document("1") // Default to floor 1 for notification
                .collection("posts")
        } else {
            db.collection("locations").document("locations")
                .collection("outdoor_locations").document(location.id)
                .collection("posts")
        }

        query.limit(1).get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                val storyName = doc.getString("name") ?: "New Story"
                sendDiscoveryNotification(location.locationName, storyName)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // Update Metadata
        intent?.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
        intent?.getStringExtra(EXTRA_USER)?.let { currentUser = it }
        intent?.getStringExtra(EXTRA_LOCATION)?.let { currentLocationName = it }

        when (action) {
            ACTION_START_TRACKING -> {
                startLocationUpdates()
                // Show a foreground notification immediately so the service stays alive
                showPlayerNotification(isPlaying)
            }
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_AUDIO_URL)
                if (url != null) playAudio(url) else resumeAudio()
                startLocationUpdates() // Ensure tracking is on when playing
            }
            ACTION_PAUSE -> {
                pauseAudio()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Check every 5s
            .setMinUpdateDistanceMeters(5f) // Or every 5 meters
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("AudioService", "Permission missing for background location", e)
        }
    }

    // --- Audio Logic ---
    private fun playAudio(url: String) {
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
    }

    private fun resumeAudio() {
        mediaPlayer?.start()
        isPlaying = true
        showPlayerNotification(true)
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        showPlayerNotification(false)
    }

    // --- Notification Logic ---

    // 1. Persistent Player Notification (Keeps Service Alive)
    private fun showPlayerNotification(isPlaying: Boolean) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager, CHANNEL_ID, "Background Audio")

        val toggleIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val pendingIntent = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val content = if (currentLocationName.isNotEmpty()) "$currentUser • $currentLocationName" else currentUser

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(currentTitle)
            .setContentText(content)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0)
                .setMediaSession(mediaSession.sessionToken))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pendingIntent
            )
            .setOngoing(true) // Required for Foreground Service
            .build()

        startForeground(101, notification)
    }

    // 2. Discovery Notification (Heads-up alert)
    private fun sendDiscoveryNotification(locationName: String, storyTitle: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager, DISCOVERY_CHANNEL_ID, "Location Discovery")

        // Clicking this could open the App/AudioScreen
        // val intent = Intent(this, MainActivity::class.java) ...

        val notification = NotificationCompat.Builder(this, DISCOVERY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("📍 You found $locationName!")
            .setContentText("Listen to: $storyTitle")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        manager.notify(200, notification)
    }

    private fun createChannel(manager: NotificationManager, id: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
            if (id == DISCOVERY_CHANNEL_ID) {
                channel.importance = NotificationManager.IMPORTANCE_HIGH
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession.release()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}