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

/** Keeps WebAudio decks and the native DSP lane alive independently of Activity visibility. */
@UnstableApi
class ScLivePlaybackService : Service() {
    inner class LocalBinder : Binder() {
        val service: ScLivePlaybackService get() = this@ScLivePlaybackService
    }

    private val binder = LocalBinder()
    lateinit var player: ScLiveNativePlayer
        private set

    @Volatile private var webAudioActive = false
    @Volatile private var nativeDspActive = false
    private var foregroundStarted = false

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
        intent?.let {
            if (it.hasExtra(EXTRA_WEB_AUDIO_ACTIVE)) webAudioActive = it.getBooleanExtra(EXTRA_WEB_AUDIO_ACTIVE, false)
            if (it.hasExtra(EXTRA_NATIVE_DSP_ACTIVE)) nativeDspActive = it.getBooleanExtra(EXTRA_NATIVE_DSP_ACTIVE, false)
        }
        if (webAudioActive || nativeDspActive) ensureForeground() else stopIfIdle()
        return START_NOT_STICKY
    }

    fun setWebAudioActive(active: Boolean) {
        webAudioActive = active
        if (active) ensureForeground() else stopIfIdle()
    }

    fun setNativeDspActive(active: Boolean) {
        nativeDspActive = active
        if (active) ensureForeground() else stopIfIdle()
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SC LIVE 2")
            .setContentText("Reproducción de audio activa")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun stopIfIdle() {
        if (webAudioActive || nativeDspActive) return
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WEB_AUDIO_ACTIVE = "com.sclive2.dj.extra.WEB_AUDIO_ACTIVE"
        const val EXTRA_NATIVE_DSP_ACTIVE = "com.sclive2.dj.extra.NATIVE_DSP_ACTIVE"
        private const val CHANNEL_ID = "sc_live2_playback"
        private const val NOTIFICATION_ID = 2202
    }
}
