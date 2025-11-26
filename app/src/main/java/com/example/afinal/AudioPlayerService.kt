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
import androidx.core.app.NotificationCompat

class AudioPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_AUDIO_URL = "EXTRA_AUDIO_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val CHANNEL_ID = "audio_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val audioUrl = intent?.getStringExtra(EXTRA_AUDIO_URL)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Story Audio"

        when (action) {
            ACTION_PLAY -> {
                if (audioUrl != null) {
                    playAudio(audioUrl, title)
                } else if (mediaPlayer != null) {
                    mediaPlayer?.start()
                    isPlaying = true
                    showNotification(title, isPlaying)
                }
            }
            ACTION_PAUSE -> {
                mediaPlayer?.pause()
                isPlaying = false
                showNotification(title, isPlaying)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun playAudio(url: String, title: String) {
        // Stop previous if exists
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener {
                start()
                this@AudioPlayerService.isPlaying = true
                showNotification(title, true)
            }
            setOnCompletionListener {
                this@AudioPlayerService.isPlaying = false
                showNotification(title, false)
                stopSelf() // Stop service when done
            }
        }
    }

    private fun showNotification(title: String, isPlaying: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Playback", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        // Play/Pause Action
        val actionIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            // Re-pass extras to keep state
            putExtra(EXTRA_TITLE, title)
        }
        val actionPendingIntent = PendingIntent.getService(this, 1, actionIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "Playing..." else "Paused")
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                actionPendingIntent
            )
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0))
            .setOngoing(isPlaying) // Cannot swipe away while playing
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}