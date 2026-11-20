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
 * Two-way DriveRack crossover for the independent native player.
 * LOW receives a subsonic high-pass followed by a Linkwitz-Riley-style low-pass.
 * MID/HIGH receives the complementary Linkwitz-Riley-style high-pass.
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
        val crossoverHz: Float = 100f,
        val crossoverSlope: Int = 24,
        val subsonicHz: Float = 25f,
        val lowAmpOn: Boolean = true,
        val highAmpOn: Boolean = true,
        val lowAmpVolume: Float = 0.75f,
        val highAmpVolume: Float = 0.75f,
        val masterDb: Float = 0f,
        val limiterEnabled: Boolean = true
    )

    private data class BiquadCoefficients(
        val b0: Float, val b1: Float, val b2: Float,
        val a1: Float, val a2: Float
    )
    private data class BiquadState(
        var x1: Float = 0f, var x2: Float = 0f,
        var y1: Float = 0f, var y2: Float = 0f
    )

    private val graphicFreqHz = floatArrayOf(60f, 180f, 350f, 600f, 12000f)
    @Volatile private var settings = Settings()
    private var peqCoefficients = Array(5) { identity() }
    private var peqState = Array(5) { Array(0) { BiquadState() } }
    private var lowPassCoefficients = Array(4) { identity() }
    private var highPassCoefficients = Array(4) { identity() }
    private var lowPassState = Array(4) { Array(0) { BiquadState() } }
    private var highPassState = Array(4) { Array(0) { BiquadState() } }
    private var subsonicCoefficients = identity()
    private var subsonicState = Array(0) { BiquadState() }

    fun updateSettings(value: Settings) {
        settings = value.copy(
            outputGainsDb = List(6) { i -> (value.outputGainsDb.getOrNull(i) ?: 0f).coerceIn(-24f, 6f) },
            eqGainsDb = List(5) { i -> (value.eqGainsDb.getOrNull(i) ?: 0f).coerceIn(-12f, 12f) },
            eqFreqHz = List(5) { i -> (value.eqFreqHz.getOrNull(i) ?: graphicFreqHz[i]).coerceIn(20f, 20000f) },
            eqQ = List(5) { i -> (value.eqQ.getOrNull(i) ?: 1f).coerceIn(0.25f, 12f) },
            crossoverHz = value.crossoverHz.coerceIn(40f, 400f),
            crossoverSlope = if (value.crossoverSlope in listOf(12, 24, 48)) value.crossoverSlope else 24,
            subsonicHz = value.subsonicHz.coerceIn(15f, 50f),
            lowAmpVolume = value.lowAmpVolume.coerceIn(0f, 1.5f),
            highAmpVolume = value.highAmpVolume.coerceIn(0f, 1.5f),
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
        peqState = Array(5) { Array(channels) { BiquadState() } }
        lowPassState = Array(4) { Array(channels) { BiquadState() } }
        highPassState = Array(4) { Array(channels) { BiquadState() } }
        subsonicState = Array(channels) { BiquadState() }
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val sampleRate = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(1f)
        val cfg = settings
        peqCoefficients = Array(5) { index ->
            val frequency = if (cfg.eqParametric) cfg.eqFreqHz[index] else graphicFreqHz[index]
            val q = if (cfg.eqParametric) cfg.eqQ[index] else 1f
            peakingCoefficients(frequency, cfg.eqGainsDb[index], q, sampleRate)
        }
        lowPassCoefficients = Array(4) { makeLowPassCoefficients(cfg.crossoverHz, sampleRate) }
        highPassCoefficients = Array(4) { makeHighPassCoefficients(cfg.crossoverHz, sampleRate) }
        subsonicCoefficients = makeHighPassCoefficients(cfg.subsonicHz, sampleRate)
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
        val lowDb = (cfg.outputGainsDb[0] + cfg.outputGainsDb[1]) * 0.5f
        val highDb = (cfg.outputGainsDb[2] + cfg.outputGainsDb[3] +
            cfg.outputGainsDb[4] + cfg.outputGainsDb[5]) * 0.25f
        val lowGain = dbToLinear(lowDb) * cfg.lowAmpVolume
        val highGain = dbToLinear(highDb) * cfg.highAmpVolume
        val masterGain = dbToLinear(cfg.masterDb)
        val activeStages = when (cfg.crossoverSlope) { 12 -> 1; 48 -> 4; else -> 2 }

        val frames = samples / channels
        repeat(frames) {
            var lowSum = 0f
            var highSum = 0f
            for (channel in 0 until channels) {
                val raw = if (bytesPerSample == 4) inputBuffer.float else inputBuffer.short / 32768f
                var eqSample = raw
                for (section in 0 until 5) {
                    eqSample = processBiquad(eqSample, peqCoefficients[section], peqState[section][channel])
                }

                if (cfg.lowAmpOn) {
                    var low = processBiquad(eqSample, subsonicCoefficients, subsonicState[channel])
                    for (stage in 0 until activeStages) {
                        low = processBiquad(low, lowPassCoefficients[stage], lowPassState[stage][channel])
                    }
                    lowSum += low * lowGain
                }
                if (cfg.highAmpOn) {
                    var high = eqSample
                    for (stage in 0 until activeStages) {
                        high = processBiquad(high, highPassCoefficients[stage], highPassState[stage][channel])
                    }
                    highSum += high * highGain
                }
            }

            // Sum both source channels to mono for each band, then split LOW left / MID-HIGH right.
            val lowOut = lowSum / channels
            val highOut = highSum / channels
            if (channels >= 2) {
                var left = (lowOut * masterGain).coerceIn(-1f, 1f)
                var right = (highOut * masterGain).coerceIn(-1f, 1f)
                if (cfg.limiterEnabled) {
                    left = softLimit(left)
                    right = softLimit(right)
                }
                if (bytesPerSample == 4) {
                    output.putFloat(left)
                    output.putFloat(right)
                } else {
                    output.putShort((left * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                    output.putShort((right * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                }
            } else {
                var mono = ((lowOut + highOut) * masterGain).coerceIn(-1f, 1f)
                if (cfg.limiterEnabled) mono = softLimit(mono)
                if (bytesPerSample == 4) output.putFloat(mono)
                else output.putShort((mono * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onReset() {
        peqState = Array(5) { Array(0) { BiquadState() } }
        lowPassState = Array(4) { Array(0) { BiquadState() } }
        highPassState = Array(4) { Array(0) { BiquadState() } }
        subsonicState = Array(0) { BiquadState() }
    }

    private fun processBiquad(sample: Float, c: BiquadCoefficients, state: BiquadState): Float {
        val y = c.b0 * sample + c.b1 * state.x1 + c.b2 * state.x2 -
            c.a1 * state.y1 - c.a2 * state.y2
        state.x2 = state.x1
        state.x1 = sample
        state.y2 = state.y1
        state.y1 = y
        return y
    }

    private fun makeLowPassCoefficients(freqHz: Float, sampleRate: Float): BiquadCoefficients =
        butterworthCoefficients(freqHz, sampleRate, false)

    private fun makeHighPassCoefficients(freqHz: Float, sampleRate: Float): BiquadCoefficients =
        butterworthCoefficients(freqHz, sampleRate, true)

    private fun butterworthCoefficients(freqHz: Float, sampleRate: Float, highPass: Boolean): BiquadCoefficients {
        val frequency = freqHz.coerceIn(10f, sampleRate * 0.45f)
        val omega = 2.0 * PI * frequency / sampleRate
        val cosW = cos(omega)
        val alpha = sin(omega) / (2.0 * 0.70710678118)
        val a0 = 1.0 + alpha
        val coeffs = if (highPass) {
            Triple((1.0 + cosW) / 2.0, -(1.0 + cosW), (1.0 + cosW) / 2.0)
        } else {
            Triple((1.0 - cosW) / 2.0, 1.0 - cosW, (1.0 - cosW) / 2.0)
        }
        val b0 = coeffs.first
        val b1 = coeffs.second
        val b2 = coeffs.third
        return BiquadCoefficients(
            (b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(),
            (-2.0 * cosW / a0).toFloat(), ((1.0 - alpha) / a0).toFloat()
        )
    }

    private fun peakingCoefficients(freqHz: Float, gainDb: Float, q: Float, sampleRate: Float): BiquadCoefficients {
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
        return BiquadCoefficients(
            (b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(),
            (a1 / a0).toFloat(), (a2 / a0).toFloat()
        )
    }

    private fun identity() = BiquadCoefficients(1f, 0f, 0f, 0f, 0f)
    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private fun softLimit(sample: Float): Float {
        val magnitude = kotlin.math.abs(sample)
        if (magnitude <= 0.82f) return sample
        val compressed = 0.82f + 0.18f *
            (1f - exp(-((magnitude - 0.82f) / 0.18f)))
        return kotlin.math.sign(sample) * compressed
    }
}
