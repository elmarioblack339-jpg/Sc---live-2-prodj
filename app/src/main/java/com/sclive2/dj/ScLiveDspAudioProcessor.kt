package com.sclive2.dj

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow

/**
 * PCM-only SC LIVE 2 processing stage for the independent native player.
 *
 * This processor deliberately does not attach an Android global/session-0 effect.
 * It processes only PCM buffers delivered through the native player's Media3 sink.
 * Settings can be changed while playback is running.
 */
@UnstableApi
class ScLiveDspAudioProcessor : BaseAudioProcessor() {
    data class Settings(
        val enabled: Boolean = false,
        val lowDb: Float = 0f,
        val lowMidDb: Float = 0f,
        val highMidDb: Float = 0f,
        val highDb: Float = 0f,
        val lowCrossoverHz: Float = 180f,
        val highCrossoverHz: Float = 5000f,
        val masterDb: Float = 0f,
        val limiterEnabled: Boolean = true
    )

    @Volatile private var settings = Settings()
    private var lowState = FloatArray(0)
    private var lowMidState = FloatArray(0)
    private var highMidState = FloatArray(0)
    private var lowAlpha = 0f
    private var lowMidAlpha = 0f
    private var highMidAlpha = 0f

    fun updateSettings(value: Settings) {
        settings = value.copy(
            lowDb = value.lowDb.coerceIn(-18f, 12f),
            lowMidDb = value.lowMidDb.coerceIn(-18f, 12f),
            highMidDb = value.highMidDb.coerceIn(-18f, 12f),
            highDb = value.highDb.coerceIn(-18f, 12f),
            lowCrossoverHz = value.lowCrossoverHz.coerceIn(60f, 500f),
            highCrossoverHz = value.highCrossoverHz.coerceIn(1500f, 10000f),
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
        lowState = FloatArray(inputAudioFormat.channelCount)
        lowMidState = FloatArray(inputAudioFormat.channelCount)
        highMidState = FloatArray(inputAudioFormat.channelCount)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val sr = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(1f)
        val lowHz = settings.lowCrossoverHz.coerceIn(60f, 500f)
        val highHz = settings.highCrossoverHz.coerceIn(1500f, 10000f)
        // Four bands are split by three low-pass boundaries. The middle
        // boundary is the geometric midpoint of the two user-set crossovers.
        val lowMidHz = kotlin.math.sqrt(lowHz * highHz).coerceIn(lowHz + 1f, highHz - 1f)
        lowAlpha = alphaFor(lowHz, sr)
        lowMidAlpha = alphaFor(lowMidHz, sr)
        highMidAlpha = alphaFor(highHz, sr)
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
        val lowGain = dbToLinear(cfg.lowDb)
        val lowMidGain = dbToLinear(cfg.lowMidDb)
        val highMidGain = dbToLinear(cfg.highMidDb)
        val highGain = dbToLinear(cfg.highDb)
        val masterGain = dbToLinear(cfg.masterDb)

        repeat(samples) { index ->
            val channel = index % channels
            val input = if (bytesPerSample == 4) inputBuffer.float else inputBuffer.short / 32768f
            val low = lowState[channel] + lowAlpha * (input - lowState[channel])
            lowState[channel] = low
            val lowMidCumulative = lowMidState[channel] + lowMidAlpha * (input - lowMidState[channel])
            lowMidState[channel] = lowMidCumulative
            val highMidCumulative = highMidState[channel] + highMidAlpha * (input - highMidState[channel])
            highMidState[channel] = highMidCumulative

            val lowMid = lowMidCumulative - low
            val highMid = highMidCumulative - lowMidCumulative
            val high = input - highMidCumulative
            var processed = low * lowGain +
                lowMid * lowMidGain +
                highMid * highMidGain +
                high * highGain
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
        lowState = FloatArray(0)
        lowMidState = FloatArray(0)
        highMidState = FloatArray(0)
        lowAlpha = 0f
        lowMidAlpha = 0f
        highMidAlpha = 0f
    }

    private fun alphaFor(cutoffHz: Float, sampleRate: Float): Float =
        (1f - kotlin.math.exp((-2.0 * PI * cutoffHz / sampleRate).toFloat())).coerceIn(0f, 1f)

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private fun softLimit(sample: Float): Float {
        val magnitude = kotlin.math.abs(sample)
        if (magnitude <= 0.82f) return sample
        val compressed = 0.82f + 0.18f * (1f - kotlin.math.exp(-((magnitude - 0.82f) / 0.18f)))
        return kotlin.math.sign(sample) * compressed
    }
}
