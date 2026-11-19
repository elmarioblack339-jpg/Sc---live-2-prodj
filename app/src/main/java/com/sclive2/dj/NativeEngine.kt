package com.sclive2.dj

import android.os.Build
import java.util.concurrent.CancellationException

/** Demucs v4.1.0 six-stem engine. Run all heavy methods on a background thread. */
object NativeEngine {
    fun interface ProgressListener { fun onProgress(fraction: Float, stage: String): Boolean }
    private const val LIBRARY = "sdengine_jni"
    @Volatile private var loaded = false
    val SOURCES = listOf("drums", "bass", "other", "vocals", "guitar", "piano")

    /** Base app remains usable on Android 8/9 and 32-bit phones; RANK is opt-in only on supported devices. */
    fun isSupportedDevice(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    @Synchronized private fun ensureLoaded() {
        check(isSupportedDevice()) { "RANK requiere Android 10 o superior y procesador ARM64." }
        if (!loaded) {
            System.loadLibrary(LIBRARY)
            loaded = true
        }
    }

    @JvmStatic private external fun nativeLoadModel(path: String): String
    @JvmStatic private external fun nativePrepareInput(raw: String, sampleRate: Int, channels: Int, out: String, listener: ProgressListener?): String
    @JvmStatic private external fun nativeAnalyze(path: String): DoubleArray
    @JvmStatic private external fun nativeSeparate(prepared: String, outDir: String, write: BooleanArray, threads: Int, computeThreads: Int, gpu: Boolean, listener: ProgressListener?): DoubleArray
    @JvmStatic private external fun nativeComputeInfo(computeThreads: Int, gpu: Boolean): String
    @JvmStatic private external fun nativeCancel()

    @Synchronized fun loadModel(path: String) {
        ensureLoaded()
        val error = nativeLoadModel(path)
        if (error.isNotEmpty()) throw IllegalStateException(error)
    }
    fun prepareInput(raw: String, sampleRate: Int, channels: Int, out: String, listener: ProgressListener? = null) {
        ensureLoaded()
        val error = nativePrepareInput(raw, sampleRate, channels, out, listener)
        if (error == "cancelled") throw CancellationException("cancelled")
        if (error.isNotEmpty()) throw IllegalStateException(error)
    }
    data class InputStats(val durationSec: Double, val peakDb: Double?, val lufs: Double?)
    fun analyze(prepared: String): InputStats {
        ensureLoaded()
        val values = nativeAnalyze(prepared)
        return InputStats(values[0], values[1].takeUnless { it.isNaN() }, values[2].takeUnless { it.isNaN() })
    }
    fun separate(prepared: String, outDir: String, selected: Set<String> = SOURCES.toSet(),
                 threads: Int = 1, computeThreads: Int = 0, gpu: Boolean = false,
                 listener: ProgressListener? = null): DoubleArray {
        ensureLoaded()
        require(threads >= 1)
        val write = BooleanArray(SOURCES.size) { SOURCES[it] in selected }
        return nativeSeparate(prepared, outDir, write, threads, computeThreads, gpu, listener)
    }
    fun computeInfo(gpu: Boolean = false, computeThreads: Int = 0): String {
        ensureLoaded()
        return nativeComputeInfo(computeThreads, gpu)
    }
    fun cancel() {
        if (loaded) nativeCancel()
    }
}
