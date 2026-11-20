package com.sclive2.dj

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * DriveRack DSP for the independent native player:
 * five parametric EQ sections followed by six independent crossover outputs.
 * Unassigned frequency gaps are intentionally left unpassed.
 */
@UnstableApi
class ScLiveDspAudioProcessor : BaseAudioProcessor() {
    data class Settings(
        val enabled: Boolean = false,
        val outputGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f),
        val eqGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
        val eqFreqHz: List<Float> = listOf(60f, 180f, 350f, 600f, 12000f),
        val eqQ: List<Float> = listOf(1f, 1f, 1f, 1f, 1f),
        val eqParametric: Boolean = false,
        val masterDb: Float = 0f,
        val limiterEnabled: Boolean = true
    )

    private data class PeqCoefficients(
        val b0: Float, val b1: Float, val b2: Float,
        val a1: Float, val a2: Float
    )
    private data class PeqState(
        var x1: Float = 0f, var x2: Float = 0f,
        var y1: Float = 0f, var y2: Float = 0f
    )

    private val bandLowHz = floatArrayOf(25f, 90f, 300f, 500f, 1000f, 10000f)
    private val bandHighHz = floatArrayOf(80f, 240f, 400f, 700f, 4000f, 16000f)
    private val graphicFreqHz = floatArrayOf(60f, 180f, 350f, 600f, 12000f)

    @Volatile private var settings = Settings()
    private var lowState = Array(6) { FloatArray(0) }
    private var highState = Array(6) { FloatArray(0) }
    private var lowAlpha = FloatArray(6)
    private var highAlpha = FloatArray(6)
    private var peqCoefficients = Array(5) { PeqCoefficients(1f, 0f, 0f, 0f, 0f) }
    private var peqState = Array(5) { Array(0) { PeqState() } }

    fun updateSettings(value: Settings) {
        settings = value.copy(
            outputGainsDb = List(6) { i -> (value.outputGainsDb.getOrNull(i) ?: 0f).coerceIn(-24f, 6f) },
            eqGainsDb = List(5) { i -> (value.eqGainsDb.getOrNull(i) ?: 0f).coerceIn(-12f, 12f) },
            eqFreqHz = List(5) { i -> (value.eqFreqHz.getOrNull(i) ?: graphicFreqHz[i]).coerceIn(20f, 20000f) },
            eqQ = List(5) { i -> (value.eqQ.getOrNull(i) ?: 1f).coerceIn(0.25f, 12f) },
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
        val channels = inputAudioFormat.channelCount
        lowState = Array(6) { FloatArray(channels) }
        highState = Array(6) { FloatArray(channels) }
        peqState = Array(5) { Array(channels) { PeqState() } }
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val sampleRate = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(1f)
        val cfg = settings
        for (band in 0 until 6) {
            lowAlpha[band] = alphaFor(bandLowHz[band], sampleRate)
            highAlpha[band] = alphaFor(bandHighHz[band], sampleRate)
        }
        peqCoefficients = Array(5) { index ->
            val frequency = if (cfg.eqParametric) cfg.eqFreqHz[index] else graphicFreqHz[index]
            val q = if (cfg.eqParametric) cfg.eqQ[index] else 1f
            peakingCoefficients(frequency, cfg.eqGainsDb[index], q, sampleRate)
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
        val gains = FloatArray(6) { dbToLinear(cfg.outputGainsDb[it]) }
        val masterGain = dbToLinear(cfg.masterDb)

        repeat(samples) { index ->
            val channel = index % channels
            val raw = if (bytesPerSample == 4) inputBuffer.float else inputBuffer.short / 32768f
            var eqSample = raw

            for (section in 0 until 5) {
                val c = peqCoefficients[section]
                val state = peqState[section][channel]
                val y = c.b0 * eqSample + c.b1 * state.x1 + c.b2 * state.x2 -
                    c.a1 * state.y1 - c.a2 * state.y2
                state.x2 = state.x1
                state.x1 = eqSample
                state.y2 = state.y1
                state.y1 = y
                eqSample = y
            }

            var processed = 0f
            for (band in 0 until 6) {
                val low = lowState[band][channel] +
                    lowAlpha[band] * (eqSample - lowState[band][channel])
                lowState[band][channel] = low
                val high = highState[band][channel] +
                    highAlpha[band] * (eqSample - highState[band][channel])
                highState[band][channel] = high
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
        peqState = Array(5) { Array(0) { PeqState() } }
    }

    private fun alphaFor(cutoffHz: Float, sampleRate: Float): Float =
        (1f - exp((-2.0 * PI * cutoffHz / sampleRate).toFloat())).coerceIn(0f, 1f)

    private fun peakingCoefficients(freqHz: Float, gainDb: Float, q: Float, sampleRate: Float): PeqCoefficients {
        val a = 10.0.pow((gainDb / 40f).toDouble())
        val omega = 2.0 * PI * freqHz / sampleRate
        val alpha = sin(omega) / (2.0 * q)
        val cosW = cos(omega)
        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW
        val a2 = 1.0 - alpha / a
        return PeqCoefficients(
            (b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(),
            (a1 / a0).toFloat(), (a2 / a0).toFloat()
        )
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private fun softLimit(sample: Float): Float {
        val magnitude = kotlin.math.abs(sample)
        if (magnitude <= 0.82f) return sample
        val compressed = 0.82f + 0.18f *
            (1f - exp(-((magnitude - 0.82f) / 0.18f)))
        return kotlin.math.sign(sample) * compressed
    }
}
