package com.redmagic.neocharge.animation.core

import kotlin.math.max

class BatteryPhysicsIntegrator {

    private var currentBasePercent: Int = -1
    private var decimalAccumulator: Float = 0.05f

    // 1. Auto-calibration state
    private var lastTickTimestampMs: Long = 0L
    private var calibratedDurationSec: Float = 35.0f // Fallback seed: 35s per 1%

    // 2. Power Noise Damping (Low-Pass Filter)
    private var smoothedWatts: Float = 0f

    // 3. Micro-Glide state
    private var isGliding: Boolean = false
    private var glideStartPercent: Float = 0f
    private var glideTargetPercent: Float = 0f
    private var glideProgress: Float = 1.0f

    fun update(raw: BatteryHardwareProvider.RawTelemetry, deltaTimeSec: Float): TelemetryState {
        val now = System.currentTimeMillis()

        // 100% Saturation Ceiling
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

        // Low-pass filter for power fluctuations (80% historical, 20% instant)
        smoothedWatts = if (smoothedWatts <= 0f) raw.watts else (smoothedWatts * 0.80f + raw.watts * 0.20f)

        // Integer flip detected (e.g. 72% -> 73%)
        if (currentBasePercent != -1 && currentBasePercent != raw.percent) {
            if (lastTickTimestampMs > 0L) {
                val elapsedSec = (now - lastTickTimestampMs) / 1000f
                if (elapsedSec in 10f..180f) {
                    // Auto-Calibrate: exactly how long 1% takes on your charger
                    calibratedDurationSec = elapsedSec
                }
            }
            lastTickTimestampMs = now

            // Trigger Micro-Glide over 250ms to bridge the gap seamlessly
            glideStartPercent = currentBasePercent.toFloat() + decimalAccumulator
            glideTargetPercent = raw.percent.toFloat() + 0.01f
            glideProgress = 0.0f
            isGliding = true

            currentBasePercent = raw.percent
            decimalAccumulator = 0.01f
        } else if (currentBasePercent == -1) {
            // Cold-Start: Seed pace from live wattage & 6000mAh battery capacity
            currentBasePercent = raw.percent
            lastTickTimestampMs = now
            decimalAccumulator = 0.05f

            val initialWatts = max(smoothedWatts, 15f)
            // Physics: 6000mAh at ~4V is 24Wh. Rate = Watts / 24Wh
            val percentPerHour = (initialWatts / 24f) * 100f
            val percentPerSec = percentPerHour / 3600f
            calibratedDurationSec = (1.0f / max(percentPerSec, 0.01f)).coerceIn(15f, 90f)
        }

        var finalDisplayPercentage: Float

        if (isGliding) {
            // Micro-glide ease-out (250ms duration)
            glideProgress += deltaTimeSec / 0.25f
            if (glideProgress >= 1.0f) {
                glideProgress = 1.0f
                isGliding = false
                finalDisplayPercentage = currentBasePercent.toFloat() + decimalAccumulator
            } else {
                // Cubic ease-out curve
                val t = 1.0f - (1.0f - glideProgress) * (1.0f - glideProgress)
                finalDisplayPercentage = glideStartPercent + (glideTargetPercent - glideStartPercent) * t
            }
        } else {
            // Adaptive speed advancement (rate = 1.0 / calibratedDuration)
            val currentRatePerSec = (1.0f / max(calibratedDurationSec, 10f))

            // Thermal / Wattage throttling modifier
            val wattageRatio = if (smoothedWatts > 0f) (smoothedWatts / 18f).coerceIn(0.6f, 1.8f) else 1.0f
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
