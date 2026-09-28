package com.redmagic.neocharge.animation.core

import kotlin.math.max

class BatteryPhysicsIntegrator {

    private var currentBasePercent: Int = -1
    private var decimalAccumulator: Float = 0.05f

    private var lastTickTimestampMs: Long = 0L
    private var calibratedDurationSec: Float = 45.0f

    private var smoothedWatts: Float = 0f

    private var isGliding: Boolean = false
    private var glideStartPercent: Float = 0f
    private var glideTargetPercent: Float = 0f
    private var glideProgress: Float = 1.0f

    fun update(raw: BatteryHardwareProvider.RawTelemetry, deltaTimeSec: Float): TelemetryState {
        val now = System.currentTimeMillis()

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

        smoothedWatts = if (smoothedWatts <= 0f) raw.watts else (smoothedWatts * 0.82f + raw.watts * 0.18f)

        if (currentBasePercent != -1 && currentBasePercent != raw.percent) {
            if (lastTickTimestampMs > 0L) {
                val elapsedSec = (now - lastTickTimestampMs) / 1000f
                if (elapsedSec in 10f..300f) {
                    calibratedDurationSec = elapsedSec
                }
            }
            lastTickTimestampMs = now

            glideStartPercent = currentBasePercent.toFloat() + decimalAccumulator
            glideTargetPercent = raw.percent.toFloat() + 0.01f
            glideProgress = 0.0f
            isGliding = true

            currentBasePercent = raw.percent
            decimalAccumulator = 0.01f
        } else if (currentBasePercent == -1) {
            currentBasePercent = raw.percent
            lastTickTimestampMs = now
            decimalAccumulator = 0.05f

            // Slow charger fallback seed: 60s per 1%
            val initialWatts = max(smoothedWatts, 5f)
            val percentPerHour = (initialWatts / 24f) * 100f
            val percentPerSec = percentPerHour / 3600f
            calibratedDurationSec = (1.0f / max(percentPerSec, 0.005f)).coerceIn(15f, 180f)
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
            // Adaptive rate with minimum floor so slow chargers never freeze
            val currentRatePerSec = (1.0f / max(calibratedDurationSec, 10f))
            
            // On weak chargers (<8W), give a guaranteed gentle crawl
            val effectiveRate = if (smoothedWatts in 0.1f..8.0f) {
                0.008f // Steady crawl for 5W USB ports
            } else {
                val wattageRatio = if (smoothedWatts > 0f) (smoothedWatts / 16f).coerceIn(0.4f, 1.8f) else 1.0f
                currentRatePerSec * wattageRatio
            }

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

    fun reset() {
        currentBasePercent = -1
        decimalAccumulator = 0.05f
        lastTickTimestampMs = 0L
        smoothedWatts = 0f
        isGliding = false
    }
}
