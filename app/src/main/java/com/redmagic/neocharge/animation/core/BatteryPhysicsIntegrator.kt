package com.redmagic.neocharge.animation.core

import kotlin.math.max

class BatteryPhysicsIntegrator {

    private var currentBasePercent: Int = -1
    private var decimalAccumulator: Float = 0.05f

    private var lastTickTimestampMs: Long = 0L
    private var calibratedDurationSec: Float = 35.0f

    private var smoothedWatts: Float = 0f

    // Micro-Glide Engine (400ms Buttery Ease-Out)
    private var isGliding: Boolean = false
    private var glideStartPercent: Float = 0f
    private var glideTargetPercent: Float = 0f
    private var glideProgress: Float = 1.0f

    fun update(raw: BatteryHardwareProvider.RawTelemetry, deltaTimeSec: Float): TelemetryState {
        val now = System.currentTimeMillis()

        // 100% Full Saturation Lock
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

        // Noise Damping on incoming power
        smoothedWatts = if (smoothedWatts <= 0f) raw.watts else (smoothedWatts * 0.82f + raw.watts * 0.18f)

        // Integer percentage step (e.g. 89% -> 90%)
        if (currentBasePercent != -1 && currentBasePercent != raw.percent) {
            if (lastTickTimestampMs > 0L) {
                val elapsedSec = (now - lastTickTimestampMs) / 1000f
                if (elapsedSec in 10f..180f) {
                    calibratedDurationSec = elapsedSec
                }
            }
            lastTickTimestampMs = now

            // Seamless 400ms Micro-Glide ease-out
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

            val initialWatts = max(smoothedWatts, 10f)
            val percentPerHour = (initialWatts / 24f) * 100f
            val percentPerSec = percentPerHour / 3600f
            calibratedDurationSec = (1.0f / max(percentPerSec, 0.01f)).coerceIn(15f, 90f)
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
            val currentRatePerSec = (1.0f / max(calibratedDurationSec, 10f))
            val wattageRatio = if (smoothedWatts > 0f) (smoothedWatts / 16f).coerceIn(0.5f, 1.8f) else 1.0f
            val effectiveRate = currentRatePerSec * wattageRatio

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
