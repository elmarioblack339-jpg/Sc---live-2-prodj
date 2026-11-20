package com.sclive2.dj

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow

/**
 * Six-output DriveRack crossover for the independent native player.
 * Each output receives only its declared frequency interval; gaps remain
 * intentionally unassigned, matching the SC LIVE 2 DriveRack configuration.
 */
@UnstableApi
class ScLiveDspAudioProcessor : BaseAudioProcessor() {
    data class Settings(
        val enabled: Boolean = false,
        val outputGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f),
        val masterDb: Float = 0f,
        val limiterEnabled: Boolean = true
    )

    private val bandLowHz = floatArrayOf(25f, 90f, 300f, 500f, 1000f, 10000f)
    private val bandHighHz = floatArrayOf(80f, 240f, 400f, 700f, 4000f, 16000f)

    @Volatile private var settings = Settings()
    private var lowState = Array(6) { FloatArray(0) }
    private var highState = Array(6) { FloatArray(0) }
    private var lowAlpha = FloatArray(6)
    private var highAlpha = FloatArray(6)

    fun updateSettings(value: Settings) {
        val gains = List(6) { index ->
            (value.outputGainsDb.getOrNull(index) ?: 0f).coerceIn(-24f, 6f)
        }
        settings = value.copy(
            outputGainsDb = gains,
            masterDb = value.masterDb.coerceIn(-18f, 6f)
        )
    }

    fun currentSettings(): Settings = settings

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount !in 1..2 ||
            (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
             inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        lowState = Array(6) { FloatArray(inputAudioFormat.channelCount) }
        highState = Array(6) { FloatArray(inputAudioFormat.channelCount) }
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val sampleRate = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(1f)
        for (band in 0 until 6) {
            lowAlpha[band] = alphaFor(bandLowHz[band], sampleRate)
            highAlpha[band] = alphaFor(bandHighHz[band], sampleRate)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val cfg = settings
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val samples = inputBuffer.remaining() / bytesPerSample
        val output = replaceOutputBuffer(samples * bytesPerSample).order(ByteOrder.nativeOrder())
        if (!cfg.enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        updateCoefficients()
        val channels = inputAudioFormat.channelCount
        val gains = FloatArray(6) { dbToLinear(cfg.outputGainsDb.getOrElse(it) { 0f }) }
        val masterGain = dbToLinear(cfg.masterDb)

        repeat(samples) { index ->
            val channel = index % channels
            val input = if (bytesPerSample == 4) inputBuffer.float else inputBuffer.short / 32768f
            var processed = 0f

            for (band in 0 until 6) {
                val low = lowState[band][channel] +
                    lowAlpha[band] * (input - lowState[band][channel])
                lowState[band][channel] = low

                val high = highState[band][channel] +
                    highAlpha[band] * (input - highState[band][channel])
                highState[band][channel] = high

                // Difference of the two low-pass signals forms the band-pass interval.
                processed += (high - low) * gains[band]
            }

            processed *= masterGain
            if (cfg.limiterEnabled) processed = softLimit(processed)
            processed = processed.coerceIn(-1f, 1f)
            if (bytesPerSample == 4) output.putFloat(processed)
            else output.putShort((processed * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onReset() {
        lowState = Array(6) { FloatArray(0) }
        highState = Array(6) { FloatArray(0) }
        lowAlpha = FloatArray(6)
        highAlpha = FloatArray(6)
    }

    private fun alphaFor(cutoffHz: Float, sampleRate: Float): Float =
        (1f - exp((-2.0 * PI * cutoffHz / sampleRate).toFloat())).coerceIn(0f, 1f)

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private fun softLimit(sample: Float): Float {
        val magnitude = kotlin.math.abs(sample)
        if (magnitude <= 0.82f) return sample
        val compressed = 0.82f + 0.18f *
            (1f - exp(-((magnitude - 0.82f) / 0.18f)))
        return kotlin.math.sign(sample) * compressed
    }
}
