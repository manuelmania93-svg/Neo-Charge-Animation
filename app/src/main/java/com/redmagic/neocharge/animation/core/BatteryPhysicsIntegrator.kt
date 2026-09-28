package com.redmagic.neocharge.animation.core

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max

class BatteryPhysicsIntegrator(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("neocharge_profiler", Context.MODE_PRIVATE)

    private var currentBasePercent: Int = -1
    private var decimalAccumulator: Float = 0.05f

    private var lastTickTimestampMs: Long = 0L
    private var calibratedDurationSec: Float = 35.0f

    private var smoothedWatts: Float = 0f
    private var activeProfileKey: String = "profile_fast"

    // Micro-Glide state
    private var isGliding: Boolean = false
    private var glideStartPercent: Float = 0f
    private var glideTargetPercent: Float = 0f
    private var glideProgress: Float = 1.0f

    fun update(raw: BatteryHardwareProvider.RawTelemetry, deltaTimeSec: Float): TelemetryState {
        val now = System.currentTimeMillis()

        // 100% Saturation Lock
        if (raw.percent >= 100) {
            return TelemetryState(
                basePercent = 100,
                displayPercentage = 100.00f,
                watts = raw.watts,
                tempCelsius = raw.tempCelsius,
                isConnected = raw.isPlugged,
                chargeGrade = "FULLY CHARGED"
            )
        }

        // Noise Damping (Low-Pass Filter)
        smoothedWatts = if (smoothedWatts <= 0f) raw.watts else (smoothedWatts * 0.82f + raw.watts * 0.18f)

        // Identify Charger Profile
        val currentProfile = when {
            smoothedWatts >= 25f -> "profile_turbo"
            smoothedWatts in 10f..25f -> "profile_fast"
            else -> "profile_slow"
        }

        // Switch to the saved profile memory if charger type changed
        if (currentProfile != activeProfileKey) {
            activeProfileKey = currentProfile
            calibratedDurationSec = loadSavedDuration(activeProfileKey)
        }

        // Percentage increment detected (e.g. 72% -> 73% or 89% -> 90%)
        if (currentBasePercent != -1 && currentBasePercent != raw.percent) {
            if (lastTickTimestampMs > 0L) {
                val elapsedSec = (now - lastTickTimestampMs) / 1000f
                if (elapsedSec in 12f..360f) {
                    calibratedDurationSec = elapsedSec
                    // Save learned pattern into long-term storage
                    saveLearnedDuration(activeProfileKey, elapsedSec)
                }
            }
            lastTickTimestampMs = now

            // Micro-Glide Ease-Out (400ms)
            glideStartPercent = currentBasePercent.toFloat() + decimalAccumulator
            glideTargetPercent = raw.percent.toFloat() + 0.01f
            glideProgress = 0.0f
            isGliding = true

            currentBasePercent = raw.percent
            decimalAccumulator = 0.01f
        } else if (currentBasePercent == -1) {
            // First plug-in: Recall saved profile duration or use seeded physics
            currentBasePercent = raw.percent
            lastTickTimestampMs = now
            decimalAccumulator = 0.05f

            calibratedDurationSec = loadSavedDuration(activeProfileKey)
        }

        var finalDisplayPercentage: Float

        if (isGliding) {
            glideProgress += deltaTimeSec / 0.40f
            if (glideProgress >= 1.0f) {
                glideProgress = 1.0f
                isGliding = false
                finalDisplayPercentage = currentBasePercent.toFloat() + decimalAccumulator
            } else {
                val t = 1.0f - (1.0f - glideProgress) * (1.0f - glideProgress) * (1.0f - glideProgress)
                finalDisplayPercentage = glideStartPercent + (glideTargetPercent - glideStartPercent) * t
            }
        } else {
            // Base pace from learned charger profile
            val baseRatePerSec = (1.0f / max(calibratedDurationSec, 12f))

            // Non-linear Battery Curve Multiplier (CC / CV Phase)
            val socMultiplier = when {
                currentBasePercent < 70 -> 1.00f // Fast Constant-Current phase
                currentBasePercent < 85 -> 0.75f // Thermal rise phase
                else                    -> 0.42f // Saturation Constant-Voltage trickle
            }

            // Power-Ratio scaling
            val powerRatio = when (activeProfileKey) {
                "profile_turbo" -> (smoothedWatts / 28f).coerceIn(0.7f, 1.8f)
                "profile_fast"  -> (smoothedWatts / 15f).coerceIn(0.5f, 1.4f)
                else            -> (smoothedWatts / 6f).coerceIn(0.3f, 1.2f)
            }

            val effectiveRate = baseRatePerSec * socMultiplier * powerRatio

            if (decimalAccumulator < 0.985f) {
                decimalAccumulator += effectiveRate * deltaTimeSec
            }

            finalDisplayPercentage = currentBasePercent.toFloat() + decimalAccumulator.coerceIn(0.00f, 0.99f)
        }

        return TelemetryState(
            basePercent = currentBasePercent,
            displayPercentage = finalDisplayPercentage,
            watts = smoothedWatts,
            tempCelsius = raw.tempCelsius,
            isConnected = raw.isPlugged,
            chargeGrade = raw.grade
        )
    }

    private fun loadSavedDuration(profile: String): Float {
        val defaultVal = when (profile) {
            "profile_turbo" -> 26.0f // 26s per 1%
            "profile_fast"  -> 48.0f // 48s per 1%
            else            -> 110.0f // 110s per 1% on weak USB
        }
        return prefs.getFloat(profile, defaultVal)
    }

    private fun saveLearnedDuration(profile: String, durationSec: Float) {
        val oldDuration = loadSavedDuration(profile)
        // Moving average: 70% history + 30% new measurement
        val updatedDuration = (oldDuration * 0.70f + durationSec * 0.30f).coerceIn(12f, 360f)
        prefs.edit().putFloat(profile, updatedDuration).apply()
    }

    fun reset() {
        currentBasePercent = -1
        decimalAccumulator = 0.05f
        lastTickTimestampMs = 0L
        smoothedWatts = 0f
        isGliding = false
    }
}
