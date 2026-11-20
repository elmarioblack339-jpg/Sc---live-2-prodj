package com.sclive2.dj

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.media3.common.util.UnstableApi

/** Keeps the native DSP audio lane alive independently of the Activity/WebView. */
@UnstableApi
class ScLivePlaybackService : Service() {
    inner class LocalBinder : Binder() {
        val service: ScLivePlaybackService get() = this@ScLivePlaybackService
    }
    private val binder = LocalBinder()
    lateinit var player: ScLiveNativePlayer
        private set

    override fun onCreate() {
        super.onCreate()
        player = ScLiveNativePlayer(applicationContext)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SC LIVE 2 · Reproducción", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SC LIVE 2")
            .setContentText("Audio del procesador activo")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    fun stopPlaybackForeground() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sc_live2_playback"
        private const val NOTIFICATION_ID = 2202
    }
}
