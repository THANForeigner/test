package com.example.afinal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat

class AudioPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val EXTRA_AUDIO_URL = "EXTRA_AUDIO_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_USER = "EXTRA_USER"
        const val CHANNEL_ID = "audio_playback_channel"
        const val EXTRA_LOCATION = "EXTRA_LOCATION"
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize MediaSession (Important for lock screen controls)
        mediaSession = MediaSessionCompat(this, "AudioPlayerService")
        mediaSession.isActive = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val audioUrl = intent?.getStringExtra(EXTRA_AUDIO_URL)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Unknown Story"
        val user = intent?.getStringExtra(EXTRA_USER) ?: "Student Stories"

        when (action) {
            ACTION_PLAY -> {
                if (audioUrl != null) {
                    // New song requested
                    playAudio(audioUrl, title, user)
                } else {
                    // Resume existing
                    mediaPlayer?.start()
                    isPlaying = true
                    showNotification(title, user, true)
                }
            }
            ACTION_PAUSE -> {
                mediaPlayer?.pause()
                isPlaying = false
                showNotification(title, user, false)
            }
        }
        return START_NOT_STICKY
    }

    private fun playAudio(url: String, title: String, user: String) {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(url)
                prepareAsync()
            } catch (e: Exception) {
                Log.e("AudioPlayerService", "Error setting data source", e)
            }
            setOnPreparedListener {
                start()
                this@AudioPlayerService.isPlaying = true
                showNotification(title, user, true)
            }
            setOnCompletionListener {
                this@AudioPlayerService.isPlaying = false
                showNotification(title, user, false)
                // Optional: stopSelf() if you want notification to vanish after song
            }
        }
    }

    private fun showNotification(title: String, user: String, isPlaying: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Background Audio", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        // Create Play/Pause Action Intent
        val toggleIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            // Re-pass data to keep notification state consistent
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_USER, user)
        }

        val togglePendingIntent = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Use your app icon
            .setContentTitle(title)
            .setContentText(user)
            // This style enables the "Spotify-like" media controls
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0) // Show the first action (Play/Pause) in compact mode
                .setMediaSession(mediaSession.sessionToken))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                togglePendingIntent
            )
            .setOngoing(isPlaying) // Prevent swiping away while playing
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession.release()
    }
}