package com.sclive2.dj

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Independent third playback lane. It never binds to Deck 1 or Deck 2.
 * Audio is decoded to PCM and passed through ScLiveDspAudioProcessor
 * before Android's selected output route (speaker / wired / Bluetooth).
 */
@UnstableApi
class ScLiveNativePlayer(context: Context) {
    val dsp = ScLiveDspAudioProcessor()
    private val appContext = context.applicationContext

    private val renderers = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean
        ): androidx.media3.exoplayer.audio.AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setAudioProcessors(arrayOf(dsp))
                .build()
        }
    }.setEnableDecoderFallback(true)

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderers)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
        }

    fun play(uriText: String, title: String): Boolean {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("content", "file", "https", "http")) return false
        return runCatching {
            player.setMediaItem(MediaItem.Builder().setUri(uri).setMediaId(title).build())
            player.prepare()
            player.playWhenReady = true
            true
        }.getOrDefault(false)
    }

    fun pause() { player.pause() }
    fun resume() { if (player.mediaItemCount > 0) player.play() }
    fun stop() { player.stop(); player.clearMediaItems() }
    fun seekBy(deltaMs: Long) {
        if (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
        }
    }
    fun seekTo(positionMs: Long) {
        if (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo(positionMs.coerceAtLeast(0L))
        }
    }
    fun status(): String {
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> if (player.isPlaying) "PLAYING" else "PAUSED"
            Player.STATE_ENDED -> "ENDED"
            else -> "IDLE"
        }
        val title = player.currentMediaItem?.mediaId ?: ""
        return org.json.JSONObject()
            .put("state", state)
            .put("title", title)
            .put("positionMs", player.currentPosition)
            .put("durationMs", if (player.duration == C.TIME_UNSET) 0 else player.duration)
            .put("dsp", dsp.currentSettings().enabled)
            .toString()
    }

    fun release() { player.release() }
}
