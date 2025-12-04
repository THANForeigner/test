package com.example.afinal.logic

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder // Import Binder
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

    // --- Binder for UI Communication ---
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // --- Audio Components ---
    private var mediaPlayer: MediaPlayer? = null
    var isPlaying = false // Allow reading from outside, setting only internal
        private set
    private lateinit var mediaSession: MediaSessionCompat

    // Track what is currently playing to avoid restarting
    var currentAudioUrl: String? = null
        private set
    // --- Location Components ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val allLocations = mutableListOf<LocationModel>()

    // Track Notification State
    private var currentTitle: String = "Waiting for location..."
    private var currentUser: String = "Student Stories"
    private var currentLocationName: String = ""

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation ?: return
                // Calculate Distance logic here (omitted for brevity, keep your existing logic)
                val nearest = DistanceCalculator.findNearestLocation(
                    location.latitude,
                    location.longitude,
                    allLocations
                )
            }
        }
        fetchLocationsFromFirestore()
    }

    // --- Public Methods for UI ---

    fun updateMetadata(title: String, user: String, location: String) {
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

    // Updated playAudio to support direct calls
    fun playAudio(url: String) {
        // If it's the same URL and we have a player, just resume
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

    // --- Existing Service Methods ---

    private fun fetchLocationsFromFirestore() {
        // ... (Keep your existing code) ...
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
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // ... (Keep your existing code) ...
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(5f)
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
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun createChannel(manager: NotificationManager, id: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
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