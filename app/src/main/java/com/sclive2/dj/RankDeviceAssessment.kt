package com.sclive2.dj

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * Conservative preflight only. It does not claim that the Demucs engine is installed
 * or that a device has passed a real separation/thermal stability test.
 */
object RankDeviceAssessment {
    fun assess(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)

        val runtime = Runtime.getRuntime()
        val totalRam = memory.totalMem.coerceAtLeast(0L)
        val availableRam = memory.availMem.coerceAtLeast(0L)
        val arm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }
        val androidSupported = Build.VERSION.SDK_INT >= 29
        val lowRamDevice = activityManager.isLowRamDevice
        val totalGb = totalRam / 1073741824.0
        val availableGb = availableRam / 1073741824.0

        val profile = when {
            !arm64 || !androidSupported -> "NO_COMPATIBLE"
            lowRamDevice || availableGb < 1.2 -> "INSUFFICIENT_RESOURCES"
            totalGb < 7.5 || availableGb < 2.0 -> "LIMITED"
            else -> "PRECHECK_OK"
        }

        val explanation = when (profile) {
            "NO_COMPATIBLE" -> "La arquitectura o versión de Android no cumple los requisitos previstos del motor nativo."
            "INSUFFICIENT_RESOURCES" -> "La memoria disponible es baja. RANK debe permanecer bloqueado para proteger la reproducción."
            "LIMITED" -> "Equipo de recursos limitados. Solo se podrá evaluar un procesamiento segmentado cuando el motor esté integrado."
            else -> "El equipo supera la comprobación preliminar; todavía requiere prueba real del motor."
        }

        return JSONObject()
            .put("profile", profile)
            .put("androidSdk", Build.VERSION.SDK_INT)
            .put("androidRelease", Build.VERSION.RELEASE ?: "")
            .put("manufacturer", Build.MANUFACTURER ?: "")
            .put("model", Build.MODEL ?: "")
            .put("abis", Build.SUPPORTED_ABIS.joinToString(","))
            .put("arm64", arm64)
            .put("cpuCores", runtime.availableProcessors().coerceAtLeast(1))
            .put("totalRamBytes", totalRam)
            .put("availableRamBytes", availableRam)
            .put("totalRamGb", totalGb)
            .put("availableRamGb", availableGb)
            .put("memoryClassMb", activityManager.memoryClass)
            .put("largeMemoryClassMb", activityManager.largeMemoryClass)
            .put("lowRamDevice", lowRamDevice)
            .put("isLowMemory", memory.lowMemory)
            .put("thresholdBytes", memory.threshold)
            .put("maxJavaHeapBytes", runtime.maxMemory())
            .put("profileExplanation", explanation)
            .put("engineIntegrated", false)
            .put("separationTestPassed", false)
            .toString()
    }
}
